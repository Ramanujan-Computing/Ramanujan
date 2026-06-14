# Ramanujan OOP Architecture

## Overview

Ramanujan supports Python-style OOP: class definitions with fields, method calls, object instantiation, and deletion. The pipeline is Python AST → Java IR (JSON) → C++ native execution.

---

## Field Declaration Convention

Fields are declared at class body level — not inside any method, and **not** via `self.xxx`. Any variable referenced inside a method body that is not a method parameter and not locally assigned within that method is treated as a class field.

```python
class Counter:
    value = 0             # scalar field
    history = [0, 0, 0]  # array field

    def increment(self):
        value = value + 1  # 'value' resolves to the scalar field above
```

No `__init__` method is needed. Fields get their initial values from the class-body declarations.

---

## ID Conventions

| Concept | ID Pattern | Example |
|---|---|---|
| Scalar field Variable | `class_<ClassName>_<fieldName>_var` | `class_Counter_value_var` |
| Array field Array | `class_<ClassName>_<fieldName>_arr` | `class_Counter_history_arr` |
| Method FunctionCall definition | `<className>_<methodName>` | `Counter_increment` |
| ClassDefinition entry | `<className>` (= `className` field) | `Counter` |
| Object handle | UUID string per instantiation | `"3f7a2b..."`  |
| ObjectHandleArg entity | `"objarg_<UUID>"` | `"objarg_9c4e1a..."` |

---

## IR Format (JSON)

### ClassDefinition

Emitted in `ruleEngineInput.classDefinitions[]`. The `id` equals `className`.

```json
{
  "id": "Counter",
  "className": "Counter",
  "scalarFieldNames": ["value"],
  "arrayFieldNames": ["history"]
}
```

### FunctionCall (method definition)

`classOwner` marks this as a method definition.

```json
{
  "id": "Counter_increment",
  "classOwner": "Counter",
  "arguments": [...],
  "allVariablesInMethod": [...]
}
```

### Command — object instantiation

```json
{
  "newObjectCommand": {
    "className": "Counter",
    "objectHandleId": "3f7a2b-..."
  }
}
```

### Command — method call

The call-site FunctionCall has `objectHandleId` set.

```json
{
  "functionCall": {
    "id": "Counter_increment_call_0",
    "objectHandleId": "3f7a2b-...",
    "arguments": [...]
  }
}
```

### Command — object deletion

```json
{
  "deleteObjectCommand": {
    "objectHandleId": "3f7a2b-..."
  }
}
```

### ObjectHandleArg (function parameter entity)

When a function or method declares an object-typed parameter (via Python type annotation), an `ObjectHandleArg` is emitted in `ruleEngineInput.objectHandleArgs[]`. It lives in `allVariablesInMethod` of the function definition but is **not** in `arguments`.

```json
{
  "id": "objarg_9c4e1a-...",
  "className": "Counter",
  "frameCount": 0
}
```

### FunctionCall — object arguments at call sites

When a call passes object-typed arguments, their runtime UUIDs (or `ObjectHandleArg` IDs for forwarded objects) are collected in `callerObjectHandleIds`, in the same order as the `ObjectHandleArg` entries in the definition's `allVariablesInMethod`. Scalar/array arguments remain in `arguments` as before.

```json
{
  "functionCall": {
    "id": "incrementTwice_call_0",
    "arguments": [],
    "callerObjectHandleIds": ["3f7a2b-..."]
  }
}
```

When an object parameter is forwarded to another call (callee receives an object param and passes it on), the `ObjectHandleArg`'s own `id` (`"objarg_..."`) is placed in `callerObjectHandleIds`. The runtime detects this ID in the global map as an `ObjectHandleArgRE` and resolves it dynamically to the actual UUID.

---

## Java Compiler Layer

**`PythonAstToRuleEngineInputConverter`** handles:

- `ClassDefNode` → `convertClassDef`: scans body for scalar/array field declarations, emits `Variable`/`Array` IR entries with the `class_<ClassName>_` scope prefix, registers a `ClassDefinition` in `ruleEngineInput.classDefinitions`, then compiles each method via `convertClassMethodDef`.
- `convertClassMethodDef`: like `convertFunctionDef` but skips `self`, sets `FunctionCall.classOwner`, pushes `"class_<ClassName>_"` scope so free variables in the method body resolve to class field IDs.
- `obj = MyClass()` (`CallNode` whose function name is in `classRegistry`) → emits `Command.newObjectCommand`, registers UUID → `classRegistry` entry in `objectHandleMap`.
- `obj.method(args)` (`AttributeNode`) → emits `Command.functionCall` with `objectHandleId` looked up from `objectHandleMap`.
- `DeleteNode` → emits `Command.deleteObjectCommand`.
- **Object-typed parameters** (see [Objects as Arguments](#objects-as-arguments)): when a parameter carries a type annotation matching a registered class name, an `ObjectHandleArg` is created and registered in `CodeConverter.objectHandleArgMap` under `scope + paramName`. It is added to `allVariablesInMethod` but **not** to `arguments`.
- **Call sites with object args**: `collectArg()` checks each argument — objects go into `callerObjectHandleIds`, scalar/array values go into `argumentIds`.
- **Method calls on object params**: `convertMethodCall` checks `objectHandleArgMap` when the receiver is not in `objectHandleMap`, using `ObjectHandleArg.getId()` as `objectHandleId` and `ObjectHandleArg.getClassName()` to resolve the class.

---

## C++ Native Runtime

### ObjectInstance

Per-instance storage created at `NewObjectCommand` execution time:

```cpp
struct ObjectInstance {
    std::string objectHandleId;
    std::vector<double> scalarSlotValues;   // one double per scalar field (by declaration order)
    std::vector<ArrayValue*> arraySlotValues; // one ArrayValue* per array field (deep-copied from prototype)
};
```

Initial values come from the class-level prototype `VariableRE`/`ArrayRE` entries in the global map.

### ObjectInstanceStore

Static map `objectHandleId → ObjectInstance*`. Also holds a class definition registry `className → ClassDefinition*`.

```
ObjectInstanceStore::registerClass(className, classDef*)
ObjectInstanceStore::add(objectHandleId, instance*)
ObjectInstanceStore::get(objectHandleId) → instance*
ObjectInstanceStore::remove(objectHandleId)
ObjectInstanceStore::clear()
ObjectInstanceStore::clearClasses()
```

Cleared at the start and end of each `Processor::process()` call.

### NewObjectCommandRE / DeleteObjectCommandRE

- **`NewObjectCommandRE::setFields`**: reads prototype scalar/array values from the global map using `class_<ClassName>_<field>_var/arr` IDs.
- **`NewObjectCommandRE::process`**: allocates `ObjectInstance`, deep-copies initial values, registers in `ObjectInstanceStore`.
- **`DeleteObjectCommandRE::process`**: calls `ObjectInstanceStore::remove(objectHandleId)`.

### ClassBasedFunctionCommandRE

Extends `FunctionCommandRE`. Intercepts method calls on objects.

**`setFields`** override: looks up class-level `VariableRE`/`ArrayRE` entries for all fields and stores them in `scalarFieldVars` / `arrayFieldVars`.

**`process`** override — 8 phases:

| Phase | Action |
|---|---|
| 0 | Save current field-var state; copy object's slot values into field vars |
| 1 | Save current locals |
| 2 | Copy arguments in |
| 3 | Execute method body |
| 4 | Restore locals |
| 5 | Copy-back output args |
| 6 | Cleanup |
| 7 | Copy modified field-var values back to object's slots; restore field-var state |

Phases 1–6 are handled by `FunctionCommandRE::process()`. Phases 0 and 7 are the class-specific wrapper.

**Factory (`GetFunctionCommandRE`)**: if `functionCall->objectHandleId` is non-empty, returns `ClassBasedFunctionCommandRE`; otherwise falls through to GPU/standard checks.

---

## Objects as Arguments

Functions and class methods can receive object instances as parameters. The callee can then call methods on the passed object, reading and writing its `DataContainerValue` slots exactly as if it were the receiver.

### Python syntax

Use a type annotation matching a registered class name to declare an object-typed parameter:

```python
class Counter:
    value = 0
    def increment(self):
        value = value + 1

def incrementTwice(c: Counter):
    c.increment()
    c.increment()
```

Class methods work the same way:

```python
class Runner:
    def runOnce(self, c: Counter):
        c.increment()
```

### New IR entity: `ObjectHandleArgRE`

`ObjectHandleArgRE` is the runtime analogue of `ObjectHandleArg`. It holds a single `currentObjectHandleId` string — the UUID of whichever object is bound to this parameter for the current call frame. It lives in the global map under the `ObjectHandleArg`'s `id`.

```
currentObjectHandleId  ← set by FunctionCommandRE at Phase 0.5
                       ← restored by FunctionCommandRE at Phase 5.5
```

### Extended `FunctionCommandRE::process()` phases

Two new phases are inserted around the existing 6:

| Phase | Action |
|---|---|
| 0.5 | For each `ObjectHandleArgRE` in `objectHandleArgREs`: save its current UUID; set it to the caller-supplied UUID (from `callerObjectHandleIds` or a forwarded `ObjectHandleArgRE`) |
| 1 | Save current locals |
| 2 | Copy scalar/array arguments in |
| 3 | Execute function body |
| 4 | Restore locals |
| 5 | Copy-back output args |
| 5.5 | Restore each `ObjectHandleArgRE` to its saved UUID |
| 6 | Cleanup |

Phase 0.5 resolves the caller-supplied value by checking whether the entry in `callerObjectHandleArgREs` is itself an `ObjectHandleArgRE` (forwarded param) or a literal UUID string (`callerObjectHandleLiteralIds`).

### `ClassBasedFunctionCommandRE` with object params

When a method is called on an object that is itself a parameter (not a locally instantiated object), the compiler sets `objectHandleId` on the call-site `FunctionCall` to the `ObjectHandleArg`'s `id` (`"objarg_..."`).

At `setFields` time, `ClassBasedFunctionCommandRE` checks whether this ID resolves to an `ObjectHandleArgRE` in the global map. If so, it stores the pointer and derives `className` from `ObjectHandleArgRE::getClassName()` (set from the type annotation at compile time).

At `process()` time, the actual UUID is read dynamically:

```
resolvedHandleId = objectHandleArgRE ? objectHandleArgRE->get() : objectHandleId
if resolvedHandleId == "__self__": return FunctionCommandRE::process()
inst = ObjectInstanceStore::get(resolvedHandleId)
// Phases 0 and 7 proceed with inst
```

This means all Phases 0 and 7 (slot ↔ field var) operate on whichever object the parameter is bound to at that moment, which can differ across recursive or nested calls.

### Forwarding object params

An object parameter can be forwarded to another function or method call unchanged. The compiler's `collectArg()` detects a `NameNode` that resolves to an `ObjectHandleArg` in `objectHandleArgMap` and places the `ObjectHandleArg`'s `id` into `callerObjectHandleIds`. The receiving `FunctionCommandRE` then follows the same `ObjectHandleArgRE` chain at runtime.

---

## Recursive Method Calls (`self.method()`)

A method can call itself recursively using the `self.method(args)` syntax.

### Compiler behaviour

When `convertMethodCall` sees an `AttributeNode` whose receiver is `self` and `currentClassName` is set, it emits a call-site `FunctionCall` with:

- `id` = `<currentClassName>_<methodName>` (same ID as the method definition)
- `objectHandleId` = `"__self__"` (sentinel — not a UUID)
- `arguments` = the actual argument IDs, resolved normally

```json
{
  "functionCall": {
    "id": "Accumulator_addDown",
    "objectHandleId": "__self__",
    "arguments": ["<id_of_n_minus_1>"]
  }
}
```

`classOwner` is **not** set on the call site; the runtime looks it up from the definition entry.

### Runtime behaviour

`GetFunctionCommandRE` sees a non-empty `objectHandleId` (`"__self__"`) and creates a `ClassBasedFunctionCommandRE` as usual.

In `ClassBasedFunctionCommandRE::process()`, the sentinel is detected first:

```
if objectHandleId == "__self__":
    return FunctionCommandRE::process()   // skip Phase 0 and Phase 7
```

Skipping Phases 0 and 7 is correct because:

- **Phase 0** (slot → field var) would overwrite the live, in-progress field values with stale slot values.
- **Phase 7** (field var → slot) would prematurely flush an intermediate result.

With the sentinel path, all levels of the recursive chain share the same class-level field vars, which accumulate state naturally. The **outermost** (non-`__self__`) call handles Phases 0 and 7 exactly once — loading the object's initial state before the body starts and flushing the final accumulated state to the slot after the entire chain returns.

Regular function argument save/restore (Phases 1–6) still runs for every recursive call, giving each frame its own isolated local variables.

### Mutual recursion between two methods

The same `__self__` sentinel works when method A calls method B and method B calls method A. The call-site `FunctionCall` emitted for `self.pong(...)` inside `ping` has `id = ClassName_pong` (the cross-method definition), not `ClassName_ping`. The runtime resolves it through the normal `functionInfoRE` pointer, so the correct body executes.

Only the **outermost** `ClassBasedFunctionCommandRE` (the one with a real UUID) performs Phases 0 and 7. Every `__self__` hop — whether into the same method or a different one — skips those phases and just runs the function body, leaving the class field vars live throughout the entire mutual-recursion chain.

### Field vs local isolation during recursion

| Variable type | Saved/restored per call? | Visible across recursive calls? |
|---|---|---|
| Class field (e.g. `total`) | No — not in `allVariablesInMethod` | Yes — accumulates through chain |
| Method parameter (e.g. `n`) | Yes — Phase 1 / Phase 5 | No — each frame has its own copy |
| Local variable (e.g. `n_minus_1`) | Yes — Phase 1 / Phase 4 | No — each frame has its own copy |

---

## Multiple Instances

Each `NewObjectCommandRE::process()` call deep-copies the prototype field values (scalars as `double`, arrays as `new ArrayValue(proto, false)`), so instances are fully independent.

During Phase 0/7, field var pointers are redirected to the specific instance's slot data and restored afterward, so method body code always sees exactly the calling object's state.

---

## Inheritance

### Syntax

A child class declares its parent with the standard Python parenthesis syntax:

```python
class Animal:
    age = 0
    def birthday(self):
        age = age + 1
    def getAge(self, out):
        out = age

class Dog(Animal):         # Dog inherits from Animal
    tricks = 0
    def learnTrick(self):
        tricks = tricks + 1
    def getTricks(self, out):
        out = tricks
```

### Rules and constraints

- **Parent must be defined before child** in the source file (same requirement as Python itself).
- **Single parent only** — one name in the base list is supported. Multiple inheritance is not.
- **No `super()`** and **no `self.parent_method()` from within a child method body** — those are out of scope for now. Only direct external calls (`child_obj.parent_method()`) are supported.

### Field slot layout

The child `ObjectInstance` slots are laid out parent-fields-first, child-own-fields-last:

```
Dog(Animal) — Animal has [age], Dog adds [tricks]:
  scalarSlotValues = [ age,   tricks ]
                       ^0     ^1
                       used by Animal methods   used by Dog methods
```

Multi-level inheritance follows the same rule (grandparent fields first):

```
Puppy(Dog) — Animal has [age], Dog adds [tricks], Puppy adds [size]:
  scalarSlotValues = [ age,   tricks,   size ]
                       ^0     ^1        ^2
```

### How field access works

**Compiler**: when `convertClassDef` processes a child class it:
1. Walks the ancestor chain (grandparent → … → parent) and prepends all inherited field names to the child's `ClassMeta` field lists. This sets correct slot indices.
2. Emits `Variable`/`Array` IR entries for every inherited field **under the child's own scope** (`class_Dog_age_var`). This allows:
   - `NewObjectCommandRE` to find initial values via `class_<ChildClass>_<field>_var`.
   - Child method bodies to resolve inherited field references via the `class_Dog_` scope prefix.

**Runtime**: field vars for a method call are selected by the method definition's `classOwner`:
- A parent method (`classOwner = "Animal"`) uses `class_Animal_age_var` ↔ slot[0] of the child object.
- A child method (`classOwner = "Dog"`) uses `class_Dog_age_var` / `class_Dog_tricks_var` ↔ slots[0,1].

The two workspace variables (`class_Animal_age_var` and `class_Dog_age_var`) are independent `VariableRE` objects; the object slot is the persistent truth. Phases 0 and 7 load/store the slot correctly regardless of which class's workspace is used.

### Method resolution

At compile time, `resolveMethodOwner(startClass, methodName)` walks the class registry from `startClass` up through `parentClassName` links until it finds a `ClassMeta` that declares `methodName` in its own `methodNames` set. The resolved owner class name is used as the prefix in the method call ID:

```
d.birthday()   →  classOwner resolved to "Animal"  →  id = "Animal_birthday"
d.learnTrick() →  classOwner resolved to "Dog"     →  id = "Dog_learnTrick"
```

### What is supported

| Feature | Supported |
|---|---|
| Child object calling parent method: `d.birthday()` | Yes |
| Child method body reading/writing inherited field | Yes |
| Multi-level inheritance (`Puppy(Dog)`, `Dog(Animal)`) | Yes |
| Passing child object as parent-typed parameter | Yes |
| `super()` | No |
| `self.parent_method()` inside a child method body | No |
| Multiple inheritance | No |

### Example

```python
class Animal:
    age = 0
    def birthday(self):
        age = age + 1
    def getAge(self, out):
        out = age

class Dog(Animal):
    tricks = 0
    def learnTrick(self):
        tricks = tricks + 1
    def getTricks(self, out):
        out = tricks

d = Dog()
d.birthday()
d.birthday()
d.learnTrick()
result_age = 0
result_tricks = 0
d.getAge(result_age)
d.getTricks(result_tricks)
del d
```

After execution: `result_age == 2.0`, `result_tricks == 1.0`.

---

## Processor Integration

In `Processor::process()`:
1. `ObjectInstanceStore::clearClasses()` and `ObjectInstanceStore::clear()` at the start.
2. For each `ClassDefinition` in `ruleEngineInput.classDefinitions`: `ObjectInstanceStore::registerClass(className, classDef)`.
3. Normal map/graph construction.
4. At the end (after result collection): `ObjectInstanceStore::clear()`.

---

## Examples

### Non-recursive method calls

```python
class Counter:
    value = 0

    def increment(self):
        value = value + 1

    def getValue(self, out):
        out = value

c = Counter()
c.increment()
c.increment()
out = 0
c.getValue(out)
del c
```

After execution: `out == 2.0`.

### Recursive method using a class field and a global

```python
base = 1   # global: base-case sentinel

class Accumulator:
    total = 0  # class field: accumulates across recursive calls

    def addDown(self, n):
        if n <= base:          # read global via function-scope global fallback
            total = total + n
        else:
            total = total + n
            n_minus_1 = n - 1
            self.addDown(n_minus_1)  # recursive call — emits objectHandleId "__self__"

    def getTotal(self, out):
        out = total

a = Accumulator()
a.addDown(5)
result = 0
a.getTotal(result)
del a
```

After execution: `result == 15.0` (= 5 + 4 + 3 + 2 + 1).

### Mutually recursive methods sharing a class field

```python
limit = 0   # global: stop when n reaches limit

class PingPong:
    steps = 0  # class field: counts every non-base invocation

    def ping(self, n):
        if n <= limit:
            steps = steps        # base case — no-op keeps the field intact
        else:
            steps = steps + 1
            n_minus_1 = n - 1
            self.pong(n_minus_1) # cross-method __self__ call

    def pong(self, n):
        if n <= limit:
            steps = steps
        else:
            steps = steps + 1
            n_minus_1 = n - 1
            self.ping(n_minus_1)

    def getSteps(self, out):
        out = steps

p = PingPong()
p.ping(4)
result = 0
p.getSteps(result)
del p
```

After execution: `result == 4.0`.

Call chain: `ping(4) → pong(3) → ping(2) → pong(1) → ping(0)` [base]. `steps` is incremented on each non-base call (4 times). The outermost `ClassBasedFunctionCommandRE` for `ping(4)` owns Phases 0 and 7; every `self.pong` / `self.ping` hop inside uses `__self__` and sees the same live `steps` field var.

### Object passed to a free function

```python
class Counter:
    value = 0

    def increment(self):
        value = value + 1

    def getValue(self, out):
        out = value

def incrementTwice(c: Counter):
    c.increment()
    c.increment()

counter = Counter()
incrementTwice(counter)
result = 0
counter.getValue(result)
del counter
```

After execution: `result == 2.0`.

`incrementTwice` receives `counter`'s UUID via `callerObjectHandleIds[0]`. Phase 0.5 loads it into `ObjectHandleArgRE` for `c`. Each `c.increment()` call sees `objectHandleId = "objarg_..."`, resolves to `ObjectHandleArgRE.get()` → actual UUID, then runs Phases 0 and 7 against `counter`'s `ObjectInstance`.

### Object passed to a class method

```python
class Counter:
    value = 0
    def increment(self):
        value = value + 1
    def getValue(self, out):
        out = value

class Runner:
    def runOnce(self, c: Counter):
        c.increment()

counter = Counter()
counter.increment()   # value = 1
runner = Runner()
runner.runOnce(counter)   # value = 2
result = 0
counter.getValue(result)
del counter
del runner
```

After execution: `result == 2.0`.

`Runner.runOnce` carries its own Phase 0/7 for `Runner`'s (empty) fields, while the `c.increment()` call inside it runs Phase 0/7 for `Counter`'s fields. The two sets of field vars are independent and do not interfere.

### Multiple object instances passed to one function

```python
class Accumulator:
    total = 0
    def add(self, n):
        total = total + n
    def getTotal(self, out):
        out = total

def addToAcc(acc: Accumulator, val):
    acc.add(val)

a1 = Accumulator()
a2 = Accumulator()
addToAcc(a1, 3)
addToAcc(a1, 2)   # a1.total = 5
addToAcc(a2, 3)   # a2.total = 3
result1 = 0
result2 = 0
a1.getTotal(result1)
a2.getTotal(result2)
del a1
del a2
```

After execution: `result1 == 5.0`, `result2 == 3.0`.

Each `addToAcc` call binds a different UUID into the same `ObjectHandleArgRE` for `acc` (Phase 0.5 / Phase 5.5). The two `Accumulator` instances accumulate independently.

---

Execution trace for `a.addDown(5)`:

```
outer ClassBasedFunctionCommandRE (objectHandleId = "<uuid>"):
  Phase 0 : save total_field_var (0); load slot[total] (0) → total_field_var = 0
  FunctionCommandRE::process():
    total = 0 + 5 = 5
    __self__ call (n=4): total = 5 + 4 = 9
      __self__ call (n=3): total = 9 + 3 = 12
        __self__ call (n=2): total = 12 + 2 = 14
          __self__ call (n=1): total = 14 + 1 = 15   ← base case
  Phase 7 : slot[total] ← total_field_var (15); restore total_field_var = 0

a.getTotal(result):
  Phase 0 : load slot[total] (15) → total_field_var = 15
  out = total → result = 15
  Phase 7 : slot[total] ← 15; restore total_field_var = 0
```

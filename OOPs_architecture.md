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

---

## Java Compiler Layer

**`PythonAstToRuleEngineInputConverter`** handles:

- `ClassDefNode` → `convertClassDef`: scans body for scalar/array field declarations, emits `Variable`/`Array` IR entries with the `class_<ClassName>_` scope prefix, registers a `ClassDefinition` in `ruleEngineInput.classDefinitions`, then compiles each method via `convertClassMethodDef`.
- `convertClassMethodDef`: like `convertFunctionDef` but skips `self`, sets `FunctionCall.classOwner`, pushes `"class_<ClassName>_"` scope so free variables in the method body resolve to class field IDs.
- `obj = MyClass()` (`CallNode` whose function name is in `classRegistry`) → emits `Command.newObjectCommand`, registers UUID → `classRegistry` entry in `objectHandleMap`.
- `obj.method(args)` (`AttributeNode`) → emits `Command.functionCall` with `objectHandleId` looked up from `objectHandleMap`.
- `DeleteNode` → emits `Command.deleteObjectCommand`.

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

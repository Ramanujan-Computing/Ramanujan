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

## Example

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

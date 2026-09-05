# Python Support (In Development):

[← Back to main README](../README.md)

> This is the actively developed front-end for the platform. The legacy [`ramanujan` language](ramanujan-language.md) is deprecated in favor of writing computations directly in Python.
Ramanujan now supports a subset of Python syntax. Python code is converted to Ramanujan's intermediate representation using Python's AST module.

## How Python Code is Processed:

The conversion from Python code to Ramanujan's intermediate code follows this flow:

```
Python Source Code (.py file)
        ↓
    [PythonAstInvoker]
    Writes code to temp file, invokes: python3 -c "import ast, ast2json, json; ..."
        ↓
    Python's ast.parse() generates AST
        ↓
    ast2json.ast2json() converts AST to JSON
        ↓
    JSON string returned to Java
        ↓
    [JsonAstParser]
    Parses JSON into Java AST node objects (ModuleNode, AssignNode, IfNode, etc.)
        ↓
    [PythonAstToRuleEngineInputConverter]
    Traverses AST nodes and generates RuleEngineInput structures
    (Variables, Operations, Commands, Conditions, etc.)
        ↓
    RuleEngineInput (Ramanujan Intermediate Code)
        ↓
    Execution by Ramanujan Engine
```

### Technical Details:
1. **PythonAstInvoker**: Creates a temporary `.py` file, then runs a Python snippet that:
   - Imports `ast`, `ast2json`, and `json`
   - Parses the code using `ast.parse()`
   - Converts to JSON using `ast2json.ast2json()`
   - Outputs JSON to stdout

2. **JsonAstParser**: Uses Jackson to parse the JSON and creates corresponding Java AST node objects.

3. **PythonAstToRuleEngineInputConverter**: Walks the AST tree and generates:
   - `Variable` objects for variable declarations
   - `Array` objects for list/array declarations  
   - `Operation` objects for arithmetic and assignments
   - `Condition` objects for comparisons
   - `Command` objects that link everything in execution order
   - `If`/`While` blocks for control flow
   - `FunctionCall` objects for function invocations

## Supported Python Features:

### Variables and Types
Variables are automatically declared on first assignment. Type is inferred from the assigned value:
- **Integer**: `x = 5`
- **Double/Float**: `y = 3.14` or any arithmetic operation result

```python
x = 5           # Integer
y = 3.14        # Double
z = x + y       # Double (arithmetic result)
```

### Arrays
Arrays **must** be created using list comprehensions initialized with `0`. This is the only supported form:
```python
# 1D array initialized with zeros
arr = [0 for _ in range(100)]

# 2D array (matrix)
matrix = [[0 for _ in range(10)] for _ in range(10)]

# n-dimensional arrays with nested comprehensions
tensor = [[[0 for _ in range(5)] for _ in range(10)] for _ in range(3)]

# Variable dimensions are supported
n = 100
arr = [0 for _ in range(n)]

# Array element access and assignment
arr[0] = 10
value = arr[i]
matrix[x][y] = 5
```

**Note**: Arrays cannot be initialized with explicit values like `[1, 2, 3]` - only `[0 for _ in range(n)]` form is allowed.

### Arithmetic Operations
Supported operators: `+`, `-`, `*`, `/`
```python
result = a + b * c - d / e
```

### Augmented Assignments
```python
x += 5      # x = x + 5
y -= 3      # y = y - 3
z *= 2      # z = z * 2
w /= 4      # w = w / 4
```

### Comparison Operations
Supported: `<`, `<=`, `>`, `>=`, `==`, `!=`
```python
if x > 5:
    pass
while count <= 100:
    pass
```

### Control Flow

#### If-Else Statements
```python
if x > 10:
    y = 1
else:
    y = 0
```

**Note**: `elif` is not supported. Use nested if-else instead:
```python
# Instead of elif, use nested if-else:
if x > 10:
    y = 1
else:
    if x > 5:
        y = 2
    else:
        y = 0
```

#### While Loops
```python
i = 0
while i < 100:
    # loop body
    i += 1
```

### Functions
Functions are defined using `def` and called directly. Arguments are passed by reference.

```python
def calculate(a, b, result):
    result = a + b * 2

# Function call
calculate(x, y, answer)
```

### Return Values
Functions can return values using tuple unpacking:
```python
def get_coords():
    x = 10
    y = 20
    return x, y

# Tuple unpacking to receive return values
a, b = get_coords()
```

Single return values:
```python
def compute(x):
    result = x * 2
    return result

value = compute(5)
```

## Unsupported Python Features (Current Limitations):

### 1. Return with Array Element Access
Returning an array element directly is **NOT** supported:
```python
# NOT SUPPORTED
def get_element(arr, index):
    return arr[index]  # ❌ Cannot return array element directly
```
**Workaround**: Assign to a variable first, then return:
```python
# SUPPORTED
def get_element(arr, index):
    value = arr[index]
    return value  # ✓ Return variable works
```

### 2. Function Call as Argument
Passing a function call as an argument to another function is **NOT** supported:
```python
# NOT SUPPORTED
result = outer_func(inner_func(x))  # ❌ Nested function calls not allowed
```
**Workaround**: Use intermediate variables:
```python
# SUPPORTED
temp = inner_func(x)
result = outer_func(temp)  # ✓ Works with intermediate variable
```

### 3. Return Function Call
Returning the result of a function call directly is **NOT** supported:
```python
# NOT SUPPORTED
def wrapper(x):
    return compute(x)  # ❌ Cannot return function call directly
```
**Workaround**: Assign to a variable first:
```python
# SUPPORTED
def wrapper(x):
    result = compute(x)
    return result  # ✓ Return variable works
```

### 4. Complex Function References
Only simple function names are supported (no method chains or computed function references):
```python
# NOT SUPPORTED
obj.method()           # ❌ Method calls on objects
funcs[0]()             # ❌ Function from array
getattr(obj, 'func')() # ❌ Dynamic function access
```

### 5. For Loops
`for` loops are **NOT** currently supported. Use `while` loops instead:
```python
# NOT SUPPORTED
for i in range(10):  # ❌
    pass

# SUPPORTED - Use while loop
i = 0
while i < 10:  # ✓
    i += 1
```

### 6. Classes and Objects
Object-oriented programming is not yet supported:
```python
# NOT SUPPORTED
class MyClass:  # ❌
    pass
```

### 7. Boolean Operations in Conditions
Boolean expressions with `and`/`or`/`not` are **NOT** supported:
```python
# NOT SUPPORTED
if x > 5 and y < 10:  # ❌
    pass
if not flag:          # ❌
    pass
```
**Workaround**: Use nested if statements:
```python
# SUPPORTED
if x > 5:
    if y < 10:  # ✓ Nested if for 'and' logic
        pass
```
**Note**: The underlying rule engine already has `and`/`or`/`not` condition primitives (`ConditionType.and/or/not`
in `commons`, wired up in `ConditionTypeFactory`) — they're used by the legacy `ramanujan` language. The Python
translator (`convertCondition` in `PythonAstToRuleEngineInputConverter`) just doesn't emit them yet; it only
handles a single `Compare` node and throws for `BoolOp` nodes (`and`/`or`/`not`). So this is a translator gap,
not a rule-engine limitation, and is a reasonable candidate for a near-term fix.

### 8. List Operations
Dynamic list operations are not supported:
```python
# NOT SUPPORTED
arr.append(5)   # ❌ append
arr.pop()       # ❌ pop
len(arr)        # ❌ len function
arr + other     # ❌ list concatenation
```

### 9. Strings
Strings are **NOT** supported:
```python
# NOT SUPPORTED
s = "hello"            # ❌ string assignment
s = "hello" + "world"  # ❌ string concatenation
```

### 10. Import Statements
Importing modules is not supported:
```python
# NOT SUPPORTED
import math  # ❌
from collections import deque  # ❌
```

### 11. Exception Handling
Try-except blocks are not supported:
```python
# NOT SUPPORTED
try:  # ❌
    pass
except:
    pass
```

### 12. Generators and Iterators
Generator expressions and iterator protocols are not supported.

### 13. Power and Modulo Operators
Power (`**`) and modulo (`%`) operators are **NOT** currently supported:
```python
# NOT SUPPORTED
x = 2 ** 10    # ❌ power operator
y = n % 10     # ❌ modulo operator
```

### 14. elif Keyword
The `elif` keyword is **NOT** supported. Use nested if-else:
```python
# NOT SUPPORTED
if x > 10:
    y = 1
elif x > 5:    # ❌ elif not allowed
    y = 2

# SUPPORTED - Use nested if-else
if x > 10:
    y = 1
else:
    if x > 5:  # ✓
        y = 2
```

## Complete Python Example:
```python
# Gradient descent example in Python syntax

def get_squared(x_pow, y_pow):
    if x_pow < y_pow:
        ans = y_pow - x_pow
    else:
        ans = x_pow - y_pow
    return ans

def get_test_arr(x, y, test_arr):
    it = 0
    while it < 100:
        test_arr[it] = x * it + y
        it = it + 1

# Initialize training data
train = [0 for _ in range(100)]
i = 0
while i < 100:
    train[i] = i * 1.9 + 33
    i = i + 1

# Main computation
x1 = 0.0
y1 = 0.0
j = 0
test_arr = [0 for _ in range(100)]

while j < 1000:
    get_test_arr(x1, y1, test_arr)
    # ... gradient computation logic
    j = j + 1
```



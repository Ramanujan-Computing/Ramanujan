# Python Grammar Support for Ramanujan

This implementation adds Python syntax support to the Ramanujan platform using ANTLR4 for parsing.

## Overview

The Ramanujan platform now supports both its original syntax and Python syntax for code input. The system automatically detects the syntax type and uses the appropriate parser.

## Architecture

### Components

1. **ANTLR4 Integration**: 
   - `Python3Lexer.g4` and `Python3Parser.g4` - Standard Python grammar files
   - `Python3LexerBase.java` and `Python3ParserBase.java` - Base classes with required methods
   - Generated parser and lexer classes

2. **Python to Ramanujan Conversion**:
   - `PythonToRamanujanConverter.java` - Listener that converts Python AST to Ramanujan intermediate representation
   - `PythonAwareCodeConverter.java` - Enhanced CodeConverter that supports both syntaxes

3. **Syntax Detection**:
   - Intelligent heuristics to distinguish between Python and Ramanujan syntax
   - Fallback mechanisms for ambiguous cases

## Syntax Mapping

| Python | Ramanujan | Description |
|--------|-----------|-------------|
| `x = 10` | `{x}={10};` | Variable assignment |
| `def func(a, b):` | `def func(var a:integer, var b:integer) {` | Function definition |
| `if x > 5:` | `if({x}>{5}) {` | Conditional statement |
| `while i < 10:` | `while({i}<{10}) {` | Loop statement |
| `func(a, b)` | `exec func(a, b)` | Function call |

## Usage

### Automatic Detection
```java
PythonAwareCodeConverter converter = new PythonAwareCodeConverter(factory, utils);
// Automatically detects syntax and uses appropriate parser
List<Command> commands = converter.interpret(code, ruleEngineInput, ...);
```

### Manual Mode Selection
```java
converter.setUsePythonSyntax(true);  // Force Python parsing
converter.setUsePythonSyntax(false); // Force Ramanujan parsing
```

## Testing

Run the parsing tests:
```bash
mvn compile exec:java -Dexec.mainClass="in.ramanujan.translation.codeConverter.antlr.PythonParsingTest"
```

Run the conversion demo:
```bash
mvn compile exec:java -Dexec.mainClass="in.ramanujan.translation.codeConverter.antlr.PythonConversionDemo"
```

## Example Python Code

See `ramanujan-test-codes/python-examples/` for Python equivalents of existing Ramanujan test cases.

## Implementation Status

- ✅ ANTLR4 integration and setup
- ✅ Python grammar parsing
- ✅ Syntax detection
- ✅ Basic framework for AST conversion
- 🔄 Complete AST to intermediate code conversion (placeholder implementation)
- ⏳ Full feature parity with Ramanujan syntax
- ⏳ Migration tools for existing code

## Dependencies

- ANTLR4 Runtime 4.13.1
- Python3 grammar from antlr/grammars-v4 repository

## Future Work

1. Complete implementation of AST to intermediate code conversion
2. Support for all Python language features
3. Type inference for Python variables
4. Migration tools for converting existing Ramanujan code to Python
5. Performance optimization for large codebases
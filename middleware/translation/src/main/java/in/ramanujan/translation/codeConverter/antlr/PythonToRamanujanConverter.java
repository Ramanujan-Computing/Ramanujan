package in.ramanujan.translation.codeConverter.antlr;

import in.ramanujan.enums.OperatorType;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayCommand;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.codeConverterLogicImpl.ConditionLogicConverter;
import in.ramanujan.translation.codeConverter.codeConverterLogicImpl.OperationLogicConverter;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

import static in.ramanujan.translation.codeConverter.CodeConverterLogicFactory.isConditionOperation;
import static in.ramanujan.translation.codeConverter.utils.CodeConversionUtils.*;

/**
 * Python3 to Ramanujan intermediate code converter using ANTLR listener pattern.
 * <p>
 * This class extends Python3ParserBaseListener and converts Python AST nodes to the intermediate
 * representation that the Ramanujan rule engine understands. It handles Python constructs like
 * variable assignments, control flow (if/while), function definitions and calls, and array
 * declarations with both constant and variable dimensions.
 * <p>
 * The converter maintains state using stacks to handle nested constructs properly and maps
 * Python syntax to corresponding Ramanujan rule engine objects.
 * <p>
 * Array Declaration Support:
 * - Constant dimensions: arr = [0 for _ in range(10)]
 * - Variable dimensions: arr = [0 for _ in range(n)] (where n is a previously declared variable)
 * - Multi-dimensional arrays with mixed constant/variable dimensions supported
 * <p>
 * Example usage:
 * <pre>
 *     PythonToRamanujanConverter converter = new PythonToRamanujanConverter(
 *         ruleEngineInput, debugCreator, scope, varMap, arrayMap);
 *     // Parser will call converter methods automatically during tree walking
 * </pre>
 */
public class PythonToRamanujanConverter extends Python3ParserBaseListener {
    
    // Private fields for managing conversion state
    private final RuleEngineInput ruleEngineInput;
    private final DebugLevelCodeCreator debugLevelCodeCreator;
    private final List<String> variableScope;
    private final Map<String, Variable> variableMap;
    private final Map<String, Array> arrayMap;
    private final List<Command> commands;
    
    // Separate list to track function commands - will be added to ruleEngineInput at the end
    private final List<Command> functionCommands;
    
    // Track previous commands for linking nextId - separate tracking for root and function contexts
    private Command previousRootCommand = null;
    private Command previousFunctionCommand = null;
    
    // Track commands that need their nextId set after control flow blocks complete
    private final Stack<Command> pendingNextIdCommands = new Stack<>();
    
    // Track commands that are ready to be linked after control flow blocks end
    private final Stack<Command> readyToLinkCommands = new Stack<>();
    
    // Use stacks to support nested while blocks - enables proper handling of nested loops
    private final Stack<While> whileBlockStack = new Stack<>();
    private final Stack<Integer> whileStartIndexStack = new Stack<>();

    // Use stacks to support nested if blocks and else blocks - handles complex conditional logic
    private final Stack<If> ifBlockStack = new Stack<>();
    private final Stack<Integer> ifStartIndexStack = new Stack<>();
    private final Stack<Integer> elseStartIndexStack = new Stack<>();

    // Use stacks to support nested function definitions - manages function scope properly
    private final Stack<FunctionCall> funcBlockStack = new Stack<>();
    private final Stack<Integer> funcStartIndexStack = new Stack<>();

    // Track function names to IDs for function calls - enables function resolution
    private final Map<String, String> functionNameToIdMap = new HashMap<>();
    // Map to track commands for function calls before declaration
    private final Map<String, List<Command>> pendingFunctionCommands = new HashMap<>();

    /**
     * Constructs a new PythonToRamanujanConverter with the required dependencies.
     * <p>
     * This constructor initializes all necessary state for converting Python AST to Ramanujan
     * intermediate representation. The converter will populate the provided ruleEngineInput
     * with converted elements during the parsing process.
     * <p>
     * Example usage:
     * <pre>
     *     RuleEngineInput input = new RuleEngineInput();
     *     List&lt;String&gt; scope = new ArrayList&lt;&gt;();
     *     Map&lt;String, Variable&gt; varMap = new HashMap&lt;&gt;();
     *     Map&lt;String, Array&gt; arrayMap = new HashMap&lt;&gt;();
     *
     *     PythonToRamanujanConverter converter = new PythonToRamanujanConverter(
     *         input, debugCreator, scope, varMap, arrayMap);
     * </pre>
     *
     * @param ruleEngineInput       The input object that will be populated with converted elements
     * @param debugLevelCodeCreator Optional debug code creator for generating debug output (can be null)
     * @param variableScope         List tracking current variable scope hierarchy
     * @param variableMap           Map for tracking variables by scoped name
     * @param arrayMap              Map for tracking array variables by name
     */
    public PythonToRamanujanConverter(RuleEngineInput ruleEngineInput,
                                      DebugLevelCodeCreator debugLevelCodeCreator,
                                      List<String> variableScope,
                                      Map<String, Variable> variableMap,
                                      Map<String, Array> arrayMap) {
        this.ruleEngineInput = ruleEngineInput;
        this.debugLevelCodeCreator = debugLevelCodeCreator;
        this.variableScope = variableScope;
        this.variableScope.add(""); // Initialize with empty scope for root level
        this.variableMap = variableMap;
        this.arrayMap = arrayMap;
        this.commands = new ArrayList<>();
        this.functionCommands = new ArrayList<>();
    }
    
    /**
     * Returns the list of commands generated by this converter.
     * <p>
     * Example usage:
     * <pre>
     *     PythonToRamanujanConverter converter = new PythonToRamanujanConverter();
     *     List<Command> cmds = converter.getCommands();
     * </pre>
     *
     * @return List of Command objects representing the translation output.
     */
    public List<Command> getCommands() {
        // Finalize commands by adding function commands at the end
        finalizeCombinedCommands();
        return ruleEngineInput.getCommands();
    }
    
    /**
     * Checks if we're currently inside a function definition.
     * @return true if currently parsing inside a function, false otherwise
     */
    private boolean isInsideFunction() {
        return !funcBlockStack.isEmpty();
    }
    
    /**
     * Gets the current command list size based on context.
     * @return size of functionCommands if inside function, commands otherwise
     */
    private int getCurrentCommandListSize() {
        return isInsideFunction() ? functionCommands.size() : commands.size();
    }
    
    /**
     * Gets the command at the specified index from the appropriate list based on context.
     * @param index The index of the command to retrieve
     * @return The command at the specified index
     */
    private Command getCommandAtIndex(int index) {
        if (isInsideFunction()) {
            return functionCommands.get(index);
        } else {
            return commands.get(index);
        }
    }
    
    /**
     * Adds a command to the appropriate list based on current context.
     * Root-level commands go to the main commands list.
     * Function commands go to the functionCommands list.
     * Also handles linking commands via nextId.
     * @param command The command to add
     */
    private void addCommand(Command command) {
        addCommand(command, true);
    }
    
    /**
     * Adds a command to the appropriate list based on current context with control over nextId linking.
     * Root-level commands go to the main commands list.
     * Function commands go to the functionCommands list.
     * @param command The command to add
     * @param participateInChaining Whether this command should participate in nextId chaining
     */
    private void addCommand(Command command, boolean participateInChaining) {
        if (isInsideFunction()) {
            // Link to previous function command if exists and this command participates in chaining
            if (participateInChaining && previousFunctionCommand != null) {
                previousFunctionCommand.setNextId(command.getId());
            }
            functionCommands.add(command);
            if (participateInChaining) {
                previousFunctionCommand = command;
            }
        } else {
            // Link to previous root command if exists and this command participates in chaining
            if (participateInChaining && previousRootCommand != null) {
                previousRootCommand.setNextId(command.getId());
            }
            commands.add(command);
            if (participateInChaining) {
                previousRootCommand = command;
            }
        }
        // Always add to ruleEngineInput for processing
        ruleEngineInput.getCommands().add(command);
        
        // Handle any pending nextId commands from control flow blocks
        // Only check for ready-to-link commands if this command participates in chaining
        if (participateInChaining) {
            linkPendingNextIdCommands(command);
        }
    }
    
    /**
     * Sets up command linking for control flow structures.
     * The previous command should point to the next command after the control flow block completes.
     */
    private void prepareControlFlowCommandLinking() {
        Command previousCommand = isInsideFunction() ? previousFunctionCommand : previousRootCommand;
        if (previousCommand != null) {
            pendingNextIdCommands.push(previousCommand);
        }
    }
    
    /**
     * Links pending commands to the first command after control flow blocks.
     */
    private void linkPendingNextIdCommands(Command nextCommand) {
        // First check if there are commands ready to be linked after a block ended
        if (!readyToLinkCommands.isEmpty()) {
            Command readyCommand = readyToLinkCommands.pop();
            readyCommand.setNextId(nextCommand.getId());
        }
    }
    
    /**
     * Finalizes the combined commands list by ensuring root-level commands appear first,
     * followed by function commands.
     */
    private void finalizeCombinedCommands() {
        // Clear any existing commands in the main list
        commands.clear();
        
        // Re-add commands in the correct order: root-level first, then function commands
        List<Command> allCommands = new ArrayList<>(ruleEngineInput.getCommands());
        
        for (Command command : allCommands) {
            if (functionCommands.contains(command)) {
                // This is a function command, add it later
                continue;
            } else {
                // This is a root-level command, add it first
                commands.add(command);
            }
        }
        
        // Now add all function commands at the end
        commands.addAll(functionCommands);

        // iterate through commands, and put the command first which has operationId != null, keep the order of rest of the commands same
        List<Command> reorderedCommands = new ArrayList<>();
        int firstOpIdx = -1;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getOperation() != null) {
                firstOpIdx = i;
                break;
            }
        }
        if (firstOpIdx != -1) {
            reorderedCommands.add(commands.get(firstOpIdx));
            if (firstOpIdx > 0) {
                reorderedCommands.addAll(commands.subList(0, firstOpIdx));
            }
            if (firstOpIdx + 1 < commands.size()) {
                reorderedCommands.addAll(commands.subList(firstOpIdx + 1, commands.size()));
            }
        } else {
            reorderedCommands.addAll(commands);
        }
        
        // Update ruleEngineInput to reflect the final order
        ruleEngineInput.getCommands().clear();
        ruleEngineInput.getCommands().addAll(reorderedCommands);
    }
    
    /**
     * Creates a new variable in the current scope and adds it to the rule engine input and variable map.
     * <p>
     * This method generates a unique variable ID based on the current scope and a random UUID, sets the variable's name,
     * data type, and value, and registers it in the rule engine input and the internal variable map for lookup.
     * <p>
     * Example usage:
     * <pre>
     *     Variable var = createVariable("x", "integer", 10);
     * </pre>
     *
     * @param name     The name of the variable to create (e.g., "x").
     * @param dataType The data type of the variable (e.g., "integer", "string").
     * @param value    The initial value to assign to the variable.
     * @return The created Variable object.
     */
    private Variable createVariable(String name, String dataType, Object value) {
        // Generate a unique variable ID using the current scope and a random UUID
        Variable variable = new Variable();
        variable.setId((variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "") +
                UUID.randomUUID().toString());
        variable.setName(name);
        variable.setDataType(dataType);
        variable.setValue(value);
        
        // Add the variable to the rule engine input and the variable map for lookup
        ruleEngineInput.getVariables().add(variable);
        variableMap.put(variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) + name : name, variable);

        return variable;
    }
    
    /**
     * Retrieves a variable by name from the current scope, or creates it if it does not exist.
     * <p>
     * If the variable is not found, it is automatically created with the default data type "integer" and value 0.
     * <p>
     * Example usage:
     * <pre>
     *     Variable var = getOrCreateVariable("y");
     * </pre>
     *
     * @param name The name of the variable to retrieve or create.
     * @return The existing or newly created Variable object.
     */
    private Variable getOrCreateVariable(String name) {

        Variable variable = getVariable(variableMap, name, variableScope);
        if (variable == null) {
            // Auto-create variable with integer type (default) if not found
            variable = createVariable(name, "integer", 0);
        }

        return variable;
    }
    
    /**
     * Creates a new assignment command for a variable with the specified right operand.
     *
     * @param variable The Variable object to assign a value to
     * @param assignmentRightOperand commandId for the right operand. It can either have constant, or variable, or arrayCommand.
     * @return  The created Command object representing the assignment operation
     */
    private Command createAssignmentCommandForVariable(Variable variable, String assignmentRightOperand) {
        Command command = new Command();
        command.setId(UUID.randomUUID().toString());

        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType(OperatorType.ASSIGN.getOperatorCode());

        Command varCommand = new Command();
        varCommand.setId(UUID.randomUUID().toString());
        varCommand.setVariableId(variable.getId());
        // Add intermediate command without participating in nextId chaining
        addCommand(varCommand, false);

        operation.setOperand1(varCommand.getId());
        operation.setOperand2(assignmentRightOperand);
        ruleEngineInput.getOperations().add(operation);

        command.setOperation(operation.getId());
        
        // Add final assignment command with nextId chaining
        addCommand(command, true);
        
        return command;
    }
    
    /**
     * Creates an Operation object from a binary expression with the specified operator and operands.
     * <p>
     * This method generates a new Operation with a unique ID, maps the Python operator to the
     * corresponding Ramanujan operator, and sets the operands. The operation is then registered
     * in the rule engine input for later processing.
     * <p>
     * Example usage:
     * <pre>
     *     Operation op = createOperation("+", "var1", "var2");
     *     // Creates addition operation between var1 and var2
     * </pre>
     *
     * @param operator     The Python operator (e.g., "+", "-", "==", "and")
     * @param leftOperand  The left operand of the binary expression
     * @param rightOperand The right operand of the binary expression
     * @return The created Operation object with mapped operator and operands
     */
    private Operation createOperation(String operator, String leftOperand, String rightOperand) {
        // Create new operation with unique identifier
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());

        // Map Python operator to Ramanujan equivalent and set operands
        operation.setOperatorType(mapPythonOperatorToRamanujan(operator));
        operation.setOperand1(leftOperand);
        operation.setOperand2(rightOperand);
        
        // Register the operation in the rule engine input
        ruleEngineInput.getOperations().add(operation);
        
        return operation;
    }
    
    /**
     * Maps Python operators to their corresponding Ramanujan rule engine operators.
     * <p>
     * This method provides the translation layer between Python syntax and Ramanujan's
     * internal operator representation. It handles arithmetic, comparison, and logical operators.
     * <p>
     * Supported mappings include:
     * - Arithmetic: +, -, *, /, //, %
     * - Comparison: ==, !=, <, <=, >, >=
     * - Logical: and (to &&), or (to ||), not (to !)
     * <p>
     * Example usage:
     * <pre>
     *     String ramanujanOp = mapPythonOperatorToRamanujan("and"); // Returns "&&"
     *     String ramanujanOp2 = mapPythonOperatorToRamanujan("//"); // Returns "/"
     * </pre>
     *
     * @param pythonOp The Python operator to map (e.g., "and", "//", "==")
     * @return The corresponding Ramanujan operator, or the original operator if no mapping exists
     */
    private String mapPythonOperatorToRamanujan(String pythonOp) {
        // Map Python operators to Ramanujan equivalents
        switch (pythonOp) {
            case "+": return "+";              // Addition
            case "-": return "-";              // Subtraction
            case "*": return "*";              // Multiplication
            case "/": return "/";              // Division
            case "//": return "/";             // Integer division maps to regular division
            case "%": return "%";              // Modulo
            case "==": return "==";            // Equality
            case "!=": return "!=";            // Inequality
            case "<": return "<";              // Less than
            case "<=": return "<=";            // Less than or equal
            case ">": return ">";              // Greater than
            case ">=": return ">=";            // Greater than or equal
            case "and": return "&&";           // Logical AND
            case "or": return "||";            // Logical OR
            case "not": return "!";            // Logical NOT
            default: return pythonOp;          // Return original if no mapping found
        }
    }

    private String leftEquateSide = "";
    private String rightEquateSide = "";


    /**
     * ANTLR listener method called when entering an expression statement in the Python AST.
     * <p>
     * This method handles three distinct categories of assignment statements:
     * 1. **Array Declaration**: `arr = [array comprehension]` - Creates new arrays
     * 2. **Array Element Assignment**: `arr[index] = value` - Assigns values to existing array elements
     * 3. **Variable Assignment**: `var = value` - Assigns values to variables
     * <p>
     * It validates that function calls are not used in assignments (which is not supported),
     * creates or retrieves the target variable/array, parses the assigned value, and generates
     * the appropriate command based on the assignment type.
     * <p>
     * **Category 1: Array Declaration** - Arrays MUST be declared using n-dimensional list comprehension syntax:
     * <pre>
     *     # 1D array declaration with constant dimension:
     *     arr = [0 for _ in range(10)]
     *
     *     # 1D array declaration with variable dimension:
     *     n = 5
     *     arr = [0 for _ in range(n)]
     *
     *     # 2D array declaration with constant dimensions:
     *     matrix = [[0] * 5 for _ in range(3)]
     *
     *     # 2D array declaration with variable dimensions:
     *     rows = 3
     *     cols = 5
     *     matrix = [[0] * cols for _ in range(rows)]
     *
     *     # 3D array declaration with mixed constant/variable dimensions:
     *     depth = 4
     *     cube = [[[0] * 5 for _ in range(depth)] for _ in range(3)]
     * </pre>
     *
     * **Category 2: Array Element Assignment** - Assigns values to existing array elements:
     * <pre>
     *     arr[0] = 10                        # 1D array element assignment
     *     matrix[i][j] = 5                   # Multi-dimensional array assignment
     *     cube[x][y][z] = 20                 # 3D array assignment
     * </pre>
     *
     * **Category 3: Variable Assignment** - Simple variable assignments:
     * <pre>
     *     x = 5                              # Simple integer assignment
     *     y = 3.14                           # Float assignment
     *     name = "Bob"                       # String assignment
     *     result = other_variable            # Variable-to-variable assignment
     * </pre>
     *
     * Variable Dimension Requirements (for array declarations):
     * - Variables used as dimensions MUST be declared before the array declaration
     * - Variable dimensions are resolved to their variable IDs and handled via RedefineArrayCommand
     * - Mixed constant and variable dimensions are supported in multi-dimensional arrays
     *
     * Unsupported array declarations (throws CompilationException):
     * <pre>
     *     arr = [1, 2, 3, 4, 5]              # Simple list literal - NOT ALLOWED
     *     matrix = [[1, 2], [3, 4]]          # Nested list literal - NOT ALLOWED
     *     arr = []                           # Empty list - NOT ALLOWED
     * </pre>
     *
     * Unsupported assignments (throws CompilationException):
     * <pre>
     *     x = func()                         # Function call in assignment
     *     arr[func()] = 5                    # Function call in array index
     *     arr = [0 for _ in range(undeclared_var)]  # Undeclared variable dimension
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the expression statement
     * @throws RuntimeException If a function call is detected in the assignment, if array declaration format is invalid, or if variable dimensions are not found in scope
     */
    @Override
    public void enterExpr_stmt(Python3Parser.Expr_stmtContext ctx) {
        // Handle assignment statements: x = value, arr[index] = value, or arr = [comprehension]
        if (ctx.getChildCount() >= 3 && "=".equals(ctx.getChild(1).getText())) {
            String leftSide = ctx.getChild(0).getText();
            String rightSide = ctx.getChild(2).getText();
            
            // Check if right side contains a function call - this is not allowed
            if (containsFunctionCall(ctx.getChild(2))) {
                throw new RuntimeException("CompilationException: Function calls in assignments like '" + leftSide + " = " + rightSide + "' are not supported. Use pass-by-reference: '" + rightSide.replaceAll("\\(.*\\)", "(" + leftSide + ")") + "' instead.");
            }

            // Check if left side contains function calls in array indices
            if (containsFunctionCall(ctx.getChild(0))) {
                throw new RuntimeException("CompilationException: Function calls in array indices like '" + leftSide + "' are not supported.");
            }

            // Explicitly categorize the assignment into three distinct cases:
            
            // Case 1: Array Declaration - rightSide is an array comprehension
            // Examples: arr = [0 for _ in range(10)], matrix = [[0] * 5 for _ in range(3)]
            if (isArrayDeclaration(rightSide)) {
                handleArrayDeclaration(leftSide, rightSide, ctx);
            }
            // Case 2: Array Element Assignment - leftSide has array access syntax
            // Examples: arr[0] = 5, matrix[i][j] = value
            else if (isArrayAccess(leftSide)) {
                handleArrayAssignment(leftSide, rightSide, ctx);
            }
            // Case 3: Variable Assignment - simple variable assignment
            // Examples: x = 5, name = "Bob", result = variable
            else {
                handleVariableAssignment(leftSide, rightSide, ctx);
            }
        }
    }

    /**
     * Handles simple variable assignments (non-array, non-array-declaration).
     * <p>
     * This method processes simple variable assignments like "x = 5" or "name = 'John'".
     * It creates or retrieves the variable, parses the value, and generates the assignment command.
     * <p>
     * Supported assignment examples:
     * <pre>
     *     x = 5                              # Simple integer assignment
     *     y = 3.14                           # Float assignment
     *     result = other_variable            # Variable-to-variable assignment
     * </pre>
     *
     * @param varName   The name of the variable being assigned to
     * @param rightSide The value being assigned (right side of =)
     * @param ctx       The parse tree context for debugging
     */
    private void handleVariableAssignment(String varName, String rightSide, Python3Parser.Expr_stmtContext ctx) {
        // Get or create the variable in current scope
        Variable variable = getOrCreateVariable(varName);

        Command value = evaluateExpr(rightSide);

        createAssignmentCommandForVariable(variable, value.getId());

        // Add debug output if debug creator is available
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat(varName + " = " + rightSide + ";");
            debugLevelCodeCreator.nextLine();
        }
    }
    
    /**
     * Handles array element assignments.
     * <p>
     * This method processes array assignments like "arr[0] = 5" or "matrix[i][j] = value".
     * It extracts the array name and indices, validates that indices are simple (no operations),
     * creates or retrieves the array, and generates the appropriate array assignment command.
     * <p>
     * IMPORTANT: Array indices MUST be simple values, variables, or array elements only:
     * <pre>
     *     # Allowed array indices:
     *     arr[0] = 5          # Literal integer index
     *     arr[i] = 10         # Variable index
     *     arr[other[j]] = 15  # Array element as index
     *     matrix[x][y] = 20   # Multiple simple indices
     * </pre>
     *
     * Unsupported array indices (throws CompilationException):
     * <pre>
     *     arr[x + 1] = 5      # Expression with operation - NOT ALLOWED
     *     arr[i * 2] = 10     # Expression with operation - NOT ALLOWED
     *     arr[func()] = 15    # Function call - NOT ALLOWED
     *     arr[x - y] = 20     # Expression with operation - NOT ALLOWED
     * </pre>
     *
     * @param leftSide  The left side of the assignment (e.g., "arr[0]", "matrix[i][j]")
     * @param rightSide The value being assigned
     * @param ctx       The parse tree context for debugging
     * @throws RuntimeException If array indices contain operations or function calls
     */
    private void handleArrayAssignment(String leftSide, String rightSide, Python3Parser.Expr_stmtContext ctx) {
        // Extract array name and indices
        String arrayName = extractArrayName(leftSide);
        List<String> indices = extractArrayIndices(leftSide);

        // Validate that all indices are simple (no operations or function calls)
        validateArrayIndices(indices, leftSide);

        // Get or create the array
        Array array = getArray(arrayMap, arrayName, variableScope);

        if (array == null) {
            throw new RuntimeException("CompilationException: Array '" + arrayName + "' not declared. " +
                    "Use the required n-dimensional list comprehension syntax to declare arrays.");
        }

        // Validate indices count against array dimensions
        if (array.getDimension() != null && indices.size() > array.getDimension().size()) {
            throw new RuntimeException("CompilationException: Too many indices for array '" + arrayName +
                "'. Expected " + array.getDimension().size() + " dimensions, got " + indices.size());
        }

        // Parse the assigned value
        Command valueSetCommand = evaluateExpr(rightSide);
        // Create array assignment command
        createArrayAssignmentCommand(array, indices, valueSetCommand.getId());

        // Add debug output
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat(leftSide + " = " + rightSide + ";");
            debugLevelCodeCreator.nextLine();
        }
    }

    /**
     * Handles array declarations using the n-dimensional list comprehension syntax.
     * <p>
     * This method validates the array declaration format and creates a new Array object
     * with the specified dimensions. It supports both constant and variable dimensions.
     * When variable dimensions are detected, it creates a RedefineArrayCommand.
     * <p>
     * Supported formats:
     * <pre>
     *     arr = [0 for _ in range(10)]                     # 1D array with constant dimension
     *     arr = [0 for _ in range(n)]                      # 1D array with variable dimension
     *     matrix = [[0] * 5 for _ in range(3)]             # 2D array with constant dimensions
     *     matrix = [[0] * m for _ in range(n)]             # 2D array with variable dimensions
     *     cube = [[[0] * 5 for _ in range(4)] for _ in range(3)]  # 3D array with constant dimensions
     * </pre>
     *
     * @param arrayName The name of the array variable being declared
     * @param declaration The array declaration expression
     * @param ctx The parse tree context for debugging
     * @throws RuntimeException If the declaration format is invalid
     */
    private void handleArrayDeclaration(String arrayName, String declaration, Python3Parser.Expr_stmtContext ctx) {
        ArrayDeclarationInfo info = validateArrayDeclarationFormat(declaration);
        
        if (info == null) {
            throw new RuntimeException("CompilationException: Invalid array declaration format for '" + arrayName + 
                " = " + declaration + "'. Use the required n-dimensional list comprehension syntax: " +
                "arr = [value for _ in range(dim)] for 1D, [[value] * dim2 for _ in range(dim1)] for 2D, etc.");
        }

        // Create the array with validated dimensions and data type
        Array array = createArray(arrayName, info.dataType, info.dimensions);

        // If any dimension is variable, create a RedefineArrayCommand
        if (info.hasVariableDimensions) {
            RedefineArrayCommand redefineCmd = new RedefineArrayCommand();
            redefineCmd.setId(UUID.randomUUID().toString());
            redefineCmd.setArrayId(array.getId());
            
            // Convert dimension expressions to command IDs where needed
            List<String> resolvedDimensions = new ArrayList<>();
            for (String dimExpr : info.dimensionExpressions) {
                try {
                    // If it's a constant, keep it as is
                    Integer.parseInt(dimExpr);
                    resolvedDimensions.add(dimExpr);
                } catch (NumberFormatException e) {
                    // It's a variable, resolve to variable ID
                    Variable dimVar = getVariable(variableMap, dimExpr, variableScope);
                    if (dimVar != null) {
                        resolvedDimensions.add(dimVar.getId());
                    } else {
                        throw new RuntimeException("CompilationException: Variable dimension '" + dimExpr + "' not found in scope.");
                    }
                }
            }
            
            redefineCmd.setNewDimensions(resolvedDimensions);
            
            // Create command for redefine array operation
            Command command = new Command();
            command.setId(UUID.randomUUID().toString());
            command.setRedefineArrayCommand(redefineCmd);
            
            addCommand(command);
        }

        // Add debug output
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat(arrayName + " = " + declaration + ";");
            debugLevelCodeCreator.nextLine();
        }
    }

    /**
     * Validates that array indices are simple (no function calls or operations in string form).
     */
    private void validateArrayIndices(List<String> indices, String leftSide) {
        for (String index : indices) {
            // Disallow function call syntax in index
            if (index.contains("(") || index.contains(")")) {
                throw new RuntimeException("CompilationException: Invalid array index '" + index +
                    "' in assignment '" + leftSide + "'. Function calls are not allowed in indices.");
            }
            // Disallow arithmetic or logical operators in index string
            if (index.matches(".*[+*/%&|<>!-].*")) {
                throw new RuntimeException("CompilationException: Invalid array index '" + index +
                    "' in assignment '" + leftSide + "'. Only simple indices are allowed.");
            }
        }
    }

    /**
     * ANTLR listener method called when entering a while statement in the Python AST.
     * <p>
     * This method handles while loop constructs by creating a While block object, parsing
     * the loop condition, and setting up tracking for commands within the loop body.
     * Uses stacks to properly handle nested while loops.
     * <p>
     * Example Python code handled:
     * <pre>
     *     while x < 10:
     *         x = x + 1
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the while statement
     */
    @Override
    public void enterWhile_stmt(Python3Parser.While_stmtContext ctx) {
        // Handle while loops: while condition:
        if (ctx.getChildCount() >= 3) {
            // Prepare control flow command linking - commands before this while block should 
            // point to commands after the while block, not to commands inside the block
            prepareControlFlowCommandLinking();
            
            // Extract the condition text from the second child (after 'while')
            String conditionText = extractConditionText(ctx.getChild(1));

            // Create a While block with unique identifier
            While whileBlock = new While();
            whileBlock.setId(UUID.randomUUID().toString());

            // Parse the condition and link it to the while block
            Condition condition = parseCondition(conditionText);
            if (condition != null) {
                whileBlock.setConditionId(condition.getId());
            }

            // Store the current command count to track the starting command
            int startingCommandIndex = getCurrentCommandListSize();

            // Add debug output for while loop start
            if (debugLevelCodeCreator != null) {
                debugLevelCodeCreator.concat("while(" + conditionText + ") {");
            }

            // Push the current while block and index onto the stack for nested loop support
            whileBlockStack.push(whileBlock);
            whileStartIndexStack.push(startingCommandIndex);

            // Register the while block in the rule engine input
            ruleEngineInput.getWhileBlocks().add(whileBlock);
        }
    }
    
    /**
     * ANTLR listener method called when exiting a while statement in the Python AST.
     * <p>
     * This method completes the while loop processing by linking the while block to its
     * starting command and cleaning up the tracking stacks. This ensures proper nesting
     * support for multiple while loops.
     *
     * @param ctx The ANTLR parse tree context for the while statement
     */
    @Override
    public void exitWhile_stmt(Python3Parser.While_stmtContext ctx) {
        // Pop the last while block and index from the stack
        if (!whileBlockStack.isEmpty() && !whileStartIndexStack.isEmpty()) {
            While whileBlock = whileBlockStack.pop();
            int startingCommandIndex = whileStartIndexStack.pop();

            // Set the starting command ID if any commands were created within this while block
            if (getCurrentCommandListSize() > startingCommandIndex) {
                whileBlock.setWhileCommandId(getCommandAtIndex(startingCommandIndex).getId());
            }
        }

        // Move pending command to ready-to-link queue when the while block ends
        if (!pendingNextIdCommands.isEmpty()) {
            Command pendingCommand = pendingNextIdCommands.pop();
            readyToLinkCommands.push(pendingCommand);
        }

        // Add debug output for while loop end
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat("}");
        }
    }
    
    /**
     * ANTLR listener method called when entering an if statement in the Python AST.
     * <p>
     * This method handles if-else constructs by creating an If block object, parsing the
     * condition, and setting up tracking for commands in both if and else branches.
     * Explicitly rejects 'elif' statements which are not supported.
     * <p>
     * Supported Python code:
     * <pre>
     *     if x > 5:
     *         y = 1
     *     else:
     *         y = 2
     * </pre>
     *
     * Unsupported (throws CompilationException):
     * <pre>
     *     elif x > 10:  # Not supported
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the if statement
     * @throws RuntimeException If 'elif' is detected in the statement
     */
    @Override
    public void enterIf_stmt(Python3Parser.If_stmtContext ctx) {
        // Check for elif: if so, throw CompilationException
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof TerminalNode) {
                TerminalNode terminal = (TerminalNode) ctx.getChild(i);
                if ("elif".equals(terminal.getText())) {
                    throw new RuntimeException("CompilationException: 'elif' is not supported. Use nested if-else instead.");
                }
            }
        }

        // Handle if statements: if condition: block ('else' ':' block)?
        if (ctx.getChildCount() >= 3) {
            // Prepare control flow command linking - commands before this if block should 
            // point to commands after the if-else block, not to commands inside the block
            prepareControlFlowCommandLinking();
            
            // Extract condition from the second child (after 'if')
            String conditionText = extractConditionText(ctx.getChild(1));

            // Create if block with unique identifier
            If ifBlock = new If();
            ifBlock.setId(UUID.randomUUID().toString());

            // Parse and link the condition
            Condition condition = parseCondition(conditionText);
            if (condition != null) {
                ifBlock.setConditionId(condition.getId());
            }

            // Store the current command count to track the starting command for if block
            int startingCommandIndex = getCurrentCommandListSize();

            // Push the current if block and index onto the stack for nested if support
            ifBlockStack.push(ifBlock);
            ifStartIndexStack.push(startingCommandIndex);

            // Check if this if statement has an else clause for later processing
            boolean hasElse = false;
            for (int i = 0; i < ctx.getChildCount(); i++) {
                if (ctx.getChild(i) instanceof TerminalNode) {
                    TerminalNode terminal = (TerminalNode) ctx.getChild(i);
                    if ("else".equals(terminal.getText())) {
                        hasElse = true;
                        // Mark where else block commands will start (placeholder, updated later)
                        elseStartIndexStack.push(-1);
                        break;
                    }
                }
            }

            if (!hasElse) {
                // No else clause, push -1 to maintain stack alignment
                elseStartIndexStack.push(-1);
            }

            // Add debug output for if statement start
            if (debugLevelCodeCreator != null) {
                debugLevelCodeCreator.concat("if(" + conditionText + ") {");
            }

            // Register the if block in the rule engine input
            ruleEngineInput.getIfBlocks().add(ifBlock);
        }
    }

    /**
     * ANTLR listener method called when exiting an if statement in the Python AST.
     * <p>
     * This method completes the if-else processing by linking the if block to its
     * commands and handling debug output. It properly manages the stacks used for
     * tracking nested if statements.
     *
     * @param ctx The ANTLR parse tree context for the if statement
     */
    @Override
    public void exitIf_stmt(Python3Parser.If_stmtContext ctx) {
        // Pop the last if block and indices from the stacks
        if (!ifBlockStack.isEmpty() && !ifStartIndexStack.isEmpty() && !elseStartIndexStack.isEmpty()) {
            If ifBlock = ifBlockStack.pop();
            int ifStartingCommandIndex = ifStartIndexStack.pop();
            int elseStartingCommandIndex = elseStartIndexStack.pop();

            // Set the command ID for the if block
            if (getCurrentCommandListSize() > ifStartingCommandIndex) {
                ifBlock.setIfCommand(getCommandAtIndex(ifStartingCommandIndex).getId());
            }

            // Set the else command ID if there was an else block
            if (elseStartingCommandIndex != -1 && elseStartingCommandIndex < getCurrentCommandListSize()) {
                ifBlock.setElseCommandId(getCommandAtIndex(elseStartingCommandIndex).getId());
            }
        }

        // Move pending command to ready-to-link queue when the if block ends
        if (!pendingNextIdCommands.isEmpty()) {
            Command pendingCommand = pendingNextIdCommands.pop();
            readyToLinkCommands.push(pendingCommand);
        }

        // Add appropriate debug output based on whether there's an else clause
        if (debugLevelCodeCreator != null) {
            boolean hasElse = false;
            // Check if this if statement has an else clause
            for (int i = 0; i < ctx.getChildCount(); i++) {
                if (ctx.getChild(i) instanceof TerminalNode) {
                    TerminalNode terminal = (TerminalNode) ctx.getChild(i);
                    if ("else".equals(terminal.getText())) {
                        hasElse = true;
                        break;
                    }
                }
            }
            if (hasElse) {
                debugLevelCodeCreator.concat("} else {");
                debugLevelCodeCreator.concat("}");
            } else {
                debugLevelCodeCreator.concat("}");
            }
        }
    }
    
    /**
     * ANTLR listener method called when entering a block in the Python AST.
     * <p>
     * This method detects when we're entering an else block by checking the context
     * and updates the tracking stacks accordingly. It handles else blocks for both
     * if statements and while loops (though while-else is noted but not fully implemented).
     * <p>
     * Example detection:
     * <pre>
     *     if condition:
     *         # if block
     *     else:
     *         # this block is detected here
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the block
     */
    @Override
    public void enterBlock(Python3Parser.BlockContext ctx) {
        // Check if this block is part of an else clause in an if statement
        if (ctx.getParent() instanceof Python3Parser.If_stmtContext) {
            Python3Parser.If_stmtContext ifCtx = (Python3Parser.If_stmtContext) ctx.getParent();

            boolean isElseBlock = isElseBlockInContext(ifCtx, ctx);

            if (isElseBlock && !elseStartIndexStack.isEmpty()) {
                // Update the else start index in the stack (replace the placeholder -1)
                elseStartIndexStack.pop();
                elseStartIndexStack.push(getCurrentCommandListSize());
            }
        }
        // Check if this block is part of an else clause in a while statement
        else if (ctx.getParent() instanceof Python3Parser.While_stmtContext) {
            Python3Parser.While_stmtContext whileCtx = (Python3Parser.While_stmtContext) ctx.getParent();

            boolean isElseBlock = isElseBlockInContext(whileCtx, ctx);

            if (isElseBlock) {
                // For while loops, we might need to handle else blocks differently
                // Since we're not currently tracking while-else blocks, we can add that logic here
                // For now, just log or handle as needed
                if (debugLevelCodeCreator != null) {
                    debugLevelCodeCreator.concat("/* while-else block detected */");
                }
            }
        }
    }

    /**
     * Helper method to determine if a block is an else block in the given context.
     * <p>
     * This method analyzes the parse tree structure to identify else blocks by looking
     * for the pattern: 'else' ':' block. It checks if the given block follows an 'else'
     * token in the parent context.
     * <p>
     * Algorithm:
     * 1. Find the position of the block in the parent's children
     * 2. Look backwards for an 'else' token before this block
     * 3. Handle intervening ':' tokens as expected syntax
     *
     * @param parentCtx The parent context containing the potential else block
     * @param blockCtx  The block to check if it's an else block
     * @return true if this block is an else block, false otherwise
     */
    private boolean isElseBlockInContext(org.antlr.v4.runtime.tree.ParseTree parentCtx, Python3Parser.BlockContext blockCtx) {
        // Look for the pattern: 'else' ':' block
        // We need to check if this block comes after an 'else' token
        boolean isElseBlock = false;
        int blockIndex = -1;

        // Find which child this block is
        for (int i = 0; i < parentCtx.getChildCount(); i++) {
            if (parentCtx.getChild(i) == blockCtx) {
                blockIndex = i;
                break;
            }
        }

        // Check if there's an 'else' token before this block
        if (blockIndex > 1) {
            for (int i = blockIndex - 1; i >= 0; i--) {
                if (parentCtx.getChild(i) instanceof TerminalNode) {
                    TerminalNode terminal = (TerminalNode) parentCtx.getChild(i);
                    if ("else".equals(terminal.getText())) {
                        isElseBlock = true;
                        break;
                    } else if (":".equals(terminal.getText())) {
                        // Continue looking for 'else' before ':'
                        continue;
                    } else {
                        // Hit something else, not an else block
                        break;
                    }
                }
            }
        }

        return isElseBlock;
    }

    /**
     * ANTLR listener method called when entering a function definition in the Python AST.
     * <p>
     * This method handles function definitions by creating a FunctionCall object (which
     * represents the function template), setting up a new variable scope for the function,
     * and tracking the function for later resolution of function calls.
     * <p>
     * Example Python code handled:
     * <pre>
     *     def myFunction(param1, param2):
     *         # function body
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the function definition
     */
    @Override
    public void enterFuncdef(Python3Parser.FuncdefContext ctx) {
        // Handle function definition: def func_name(params):
        if (ctx.getChildCount() >= 4) {
            // Extract function name from the second child (after 'def')
            String funcName = ctx.getChild(1).getText();

            // Create a function call structure (represents the function template)
            FunctionCall functionCall = new FunctionCall();
            functionCall.setId(UUID.randomUUID().toString());

            // Track starting command index for function body - function commands will be added to functionCommands
            int startingCommandIndex = functionCommands.size();
            funcBlockStack.push(functionCall);
            funcStartIndexStack.push(startingCommandIndex);

            // Add new scope for function - parameters will be processed by enterTfpdef
            variableScope.add(functionCall.getId());
            
            // Reset function command tracking for this new function scope
            previousFunctionCommand = null;

            // Register the function in the rule engine input
            ruleEngineInput.getFunctionCalls().add(functionCall);

            // Map function name to its ID for later resolution during function calls
            functionNameToIdMap.put(funcName, functionCall.getId());

            if (pendingFunctionCommands.containsKey(funcName)) {
                List<Command> pendingCommands = pendingFunctionCommands.get(funcName);
                for (Command cmd : pendingCommands) {
                    if (cmd.getFunctionCall() != null) {
                        cmd.getFunctionCall().setId(functionCall.getId());
                    }
                }
                pendingFunctionCommands.remove(funcName);
            }

            // Add debug output for function definition start
            if (debugLevelCodeCreator != null) {
                debugLevelCodeCreator.concat("def " + funcName + "() {");
            }
        }
    }
    
    /**
     * ANTLR listener method called when entering a function parameter definition in the Python AST.
     * <p>
     * This method handles individual function parameters by creating Variable objects
     * in the current function scope. It's called automatically by the ParseTreeWalker
     * for each parameter in a function definition.
     * <p>
     * Example Python code handled:
     * <pre>
     *     def func(param1, param2):  # Creates variables for param1 and param2
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the function parameter definition
     */
    @Override
    public void enterTfpdef(Python3Parser.TfpdefContext ctx) {
        // Handle function parameter definitions
        // This is called automatically by ParseTreeWalker for each parameter
        if (ctx.name() != null) {
            String paramName = ctx.name().getText();
            // Create parameter as a variable in current function scope with default integer type
            createVariable(paramName, "integer", null);
        }
    }

    /**
     * ANTLR listener method called when exiting a function definition in the Python AST.
     * <p>
     * This method completes function definition processing by linking the function to its
     * starting command, removing the function scope, and cleaning up the tracking stacks.
     *
     * @param ctx The ANTLR parse tree context for the function definition
     */
    @Override
    public void exitFuncdef(Python3Parser.FuncdefContext ctx) {
        // Pop function definition and starting index from stacks
        if (!funcBlockStack.isEmpty() && !funcStartIndexStack.isEmpty()) {
            FunctionCall functionCall = funcBlockStack.pop();
            int startingCommandIndex = funcStartIndexStack.pop();

            // Set the starting command ID for function entry - use functionCommands since commands inside functions go there
            if (functionCommands.size() > startingCommandIndex) {
                functionCall.setFirstCommandId(functionCommands.get(startingCommandIndex).getId());
            }
        }

        // Remove function scope from the scope stack
        if (variableScope.size() > 0) {
            variableScope.remove(variableScope.size() - 1);
        }
        
        // Reset function command tracking when exiting function scope
        previousFunctionCommand = null;

        // Add debug output for function definition end
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat("}");
        }
    }
    
    /**
     * ANTLR listener method called when entering an atom expression in the Python AST.
     * <p>
     * This method detects and handles function calls by examining atom expressions that
     * have function call trailers (parentheses). It creates FunctionCall objects, resolves
     * function references, processes arguments, and generates the appropriate commands.
     * <p>
     * Example Python code handled:
     * <pre>
     *     func()           # Simple function call
     *     func(x, y)       # Function call with arguments
     *     obj.method()     # Method call (though objects not fully supported)
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the atom expression
     */
    @Override
    public void enterAtom_expr(Python3Parser.Atom_exprContext ctx) {
        // Check if this is a function call: atom followed by '(' trailer
        if (ctx.getChildCount() >= 2) {
            // Get the atom (function name)
            Python3Parser.AtomContext atomCtx = ctx.atom();

            // Check if we have trailers - there can be multiple trailers for chained calls
            for (Python3Parser.TrailerContext trailer : ctx.trailer()) {
                // Check if this trailer is a function call (starts with '(' and ends with ')')
                if (trailer.getChildCount() >= 3 && "(".equals(trailer.getChild(0).getText()) &&
                    ")".equals(trailer.getChild(trailer.getChildCount() - 1).getText())) {

                    // Extract function name from atom
                    String functionName = null;
                    if (atomCtx.name() != null) {
                        functionName = atomCtx.name().getText();
                    }

                    if (functionName != null) {
                        // Create function call object
                        FunctionCall functionCall = new FunctionCall();
                        functionCall.setId(UUID.randomUUID().toString());

                        // Set the function ID if we know this function (from function definitions)
                        String functionId = functionNameToIdMap.get(functionName);
                        if (functionId != null) {
                            // This links to a previously defined function
                            functionCall.setId(functionId);
                        }

                        // Parse and resolve arguments
                        List<String> argumentIds = new ArrayList<>();
                        Python3Parser.ArglistContext arglist = trailer.arglist();
                        if (arglist != null) {
                            // Process each argument in the argument list
                            for (Python3Parser.ArgumentContext argCtx : arglist.argument()) {
                                String argText = argCtx.getText();
                                // Try to resolve argument as variable or literal
                                String argumentId = resolveArgument(argText);
                                argumentIds.add(argumentId);
                            }
                        }

                        functionCall.setArguments(argumentIds);

                        // Create a command for this function call
                        Command command = new Command();
                        command.setId(UUID.randomUUID().toString());
                        command.setFunctionCall(functionCall);

                        // Register the command and function call in rule engine input
                        addCommand(command);

                        if (functionId == null) {
                            pendingFunctionCommands.computeIfAbsent(functionName, k -> new ArrayList<>()).add(command);
                        }

                        // Add debug output for function call
                        if (debugLevelCodeCreator != null) {
                            StringBuilder args = new StringBuilder();
                            for (int i = 0; i < argumentIds.size(); i++) {
                                if (i > 0) args.append(", ");
                                args.append(argumentIds.get(i));
                            }
                            debugLevelCodeCreator.concat("exec " + functionName + "(" + args + ");");
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolves a function argument to either a variable ID or a literal value.
     * <p>
     * This method attempts to resolve function arguments by first checking if they
     * correspond to existing variables, and if not, treating them as literal values.
     * This enables functions to be called with both variable references and direct values.
     * <p>
     * Resolution priority:
     * 1. Check if argument matches an existing variable name
     * 2. Try to parse as a literal value (number, string)
     * 3. Return as-is if neither above applies
     * <p>
     * Example usage:
     * <pre>
     *     String id1 = resolveArgument("x");     // Returns variable ID if x exists
     *     String id2 = resolveArgument("42");    // Returns "42" as literal
     *     String id3 = resolveArgument("\"hi\""); // Returns "\"hi\"" as string literal
     * </pre>
     *
     * @param argText The argument text to resolve
     * @return The variable ID if the argument is a variable, or the literal value otherwise
     */
    private String resolveArgument(String argText) {
        // Try to find it as a variable first
        Variable variable = getVariable(variableMap, argText, variableScope);
        if (variable != null) {
            return variable.getId();
        }

       // It should be an array, if not then, its compilation error
        Array array = getArray(arrayMap, argText, variableScope);
        if (array != null) {
            //we are passing array as refence as of now.
            return array.getId();
        }

        try {
            Double doubleValue = Double.parseDouble(argText);
            Constant constant = new Constant();
            constant.setId(UUID.randomUUID().toString());
            constant.setValue(doubleValue);
            ruleEngineInput.getConstants().add(constant);
            return constant.getId();
        } catch (NumberFormatException e) {
            throw new RuntimeException("CompilationException: Invalid argument '" + argText + "'. " +
                "Arguments must be variables, arrays, or double value.");
        }
    }

    private class CodeConverterPythonForOperationResolution extends CodeConverter
    {
        @Override
        public List<Command> interpret(String code, RuleEngineInput ruleEngineInput, List<String> variableScope,
                                       DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap,
                                       Integer[] frameVariableCounterId) throws CompilationException {
            // If code is an array value retrieval, handle it as an array access. If its variable, handle it as a variable access.
            // else, if its parsable by Double, then handle it as a constant value.

            code = code.trim();
            if (isArrayAccess(code)) {
                // Handle array access
                String arrayName = extractArrayName(code);
                List<String> indices = extractArrayIndices(code);
                Array array = getArray(arrayMap, arrayName, variableScope);

                if (array == null) {
                    throw new RuntimeException("CompilationException: Array '" + arrayName + "' not declared. " +
                        "Use the required n-dimensional list comprehension syntax to declare arrays.");
                }

                if (array.getDimension() != null && indices.size() > array.getDimension().size()) {
                    throw new RuntimeException("CompilationException: Too many indices for array '" + arrayName +
                        "'. Expected " + array.getDimension().size() + " dimensions, got " + indices.size());
                }

                return createArrayAccessCommand(array, indices);
            } else {
                // Handle variable or constant
                Variable variable = getVariable(variableMap, code, variableScope);
                if (variable != null) {
                    Command command = new Command();
                    command.setId(UUID.randomUUID().toString());
                    command.setVariableId(variable.getId());
                    // Add command without participating in nextId chaining (intermediate for operation resolution)
                    addCommand(command, false);
                    return Collections.singletonList(command);
                } else {
                    try {
                        Double doubleValue = Double.parseDouble(code);
                        Constant constant = new Constant();
                        constant.setId(UUID.randomUUID().toString());
                        constant.setValue(doubleValue);
                        ruleEngineInput.getConstants().add(constant);

                        Command command = new Command();
                        command.setId(UUID.randomUUID().toString());
                        command.setConstant(constant.getId());
                        // Add command without participating in nextId chaining (intermediate for operation resolution)
                        addCommand(command, false);

                        return Collections.singletonList(command);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("CompilationException: Invalid code '" + code + "'. " +
                            "Must be a variable, array access, or double value.");
                    }
                }
            }
        }
    }

    private List<Command> createArrayAccessCommand(Array array, List<String> indices) {
        // Create a command for accessing an array element
        Command command = new Command();
        command.setId(UUID.randomUUID().toString());

        // Validate and set indices
        validateArrayIndices(indices, array.getName());

        ArrayCommand arrayCommand = new ArrayCommand();
        arrayCommand.setArrayId(array.getId());
        arrayCommand.setIndex(indices);
        command.setArrayCommand(arrayCommand);

        // Register the command in rule engine input (intermediate command for evaluation)
        addCommand(command, false);

        return Collections.singletonList(command);
    }

    /**
     * Parses the right side of an assignment statement to determine its type and create the appropriate command.
     */
    private Command evaluateExpr(String expr) throws RuntimeException {
        expr = expr.trim();

        // Check if the expression is a variable
        Variable variable = getVariable(variableMap, expr, variableScope);
        if (variable != null) {
            Command command = new Command();
            command.setId(UUID.randomUUID().toString());
            // Set the variable in the command
            command.setVariableId(variable.getId());
            // Add command without participating in nextId chaining (intermediate command for evaluation)
            addCommand(command, false);
            return command;
        }

        // Check if the expression is an array access
        String arrayName = extractArrayName(expr);
        List<String> indices = extractArrayIndices(expr);
        Array array = getArray(arrayMap, arrayName, variableScope);

        if (array != null) {
            if (array.getDimension() != null && indices.size() > array.getDimension().size()) {
                throw new RuntimeException("CompilationException: Too many indices for array '" + arrayName +
                        "'. Expected " + array.getDimension().size() + " dimensions, got " + indices.size());
            }

            return createArrayAccessCommand(array, indices).get(0);
        }

        try {
            // Try to parse the expression as a constant value
            Double doubleValue = Double.parseDouble(expr);
            Constant constant = new Constant();
            constant.setId(UUID.randomUUID().toString());
            constant.setValue(doubleValue);
            ruleEngineInput.getConstants().add(constant);

            Command command = new Command();
            command.setId(UUID.randomUUID().toString());
            // Set the constant in the command
            command.setConstant(constant.getId());
            // Add command without participating in nextId chaining (intermediate command for evaluation)
            addCommand(command, false);

            return command;
        } catch (NumberFormatException ignored) {
            // Not a variable or array, continue to check for conditions or operations
        }

        if (isConditionOperation(expr)) {
            Condition condition = null;
            ConditionLogicConverter conditionLogicConverter = new ConditionLogicConverter();
            try {
                condition = (Condition) conditionLogicConverter.convertCode(expr, ruleEngineInput, new CodeConverterPythonForOperationResolution(),
                        variableScope, debugLevelCodeCreator, new HashMap<>(), new Integer[1]);
            } catch (CompilationException e) {
                throw new RuntimeException(e);
            }

            Command command = new Command();
            command.setId(UUID.randomUUID().toString());
            // Set the condition in the command
            command.setConditionId(condition.getId());
            // Add command without participating in nextId chaining (intermediate command for evaluation)
            addCommand(command, false);

            return command;
        }

        OperationLogicConverter operationLogicConverter = new OperationLogicConverter();
        Operation operation = null;
        try {
            operation = (Operation) operationLogicConverter.convertCode(expr, ruleEngineInput, new CodeConverterPythonForOperationResolution(),
                    variableScope, debugLevelCodeCreator, new HashMap<>(), new Integer[1]);
        } catch (CompilationException e) {
            throw new RuntimeException(e);
        }
        Command command = new Command();
        command.setId(UUID.randomUUID().toString());

        // Set the operation in the command
        command.setOperation(operation.getId());

        // Register the command in rule engine input (intermediate command for evaluation)
        addCommand(command, false);

        return command;
    }
    
    /**
     * Recursively extracts condition text from a parse tree node.
     * <p>
     * This method traverses the parse tree to extract the textual representation
     * of a condition expression. It handles both terminal nodes (direct text) and
     * non-terminal nodes (by recursively extracting from children).
     * <p>
     * Example usage:
     * <pre>
     *     String condition = extractConditionText(conditionNode);
     *     // For "x < 5", returns "x<5"
     *     // For "a == b and c > d", returns "a==bandc>d"
     * </pre>
     *
     * @param node The parse tree node from which to extract condition text
     * @return The concatenated text representation of the condition
     */
    private String extractConditionText(org.antlr.v4.runtime.tree.ParseTree node) {
        // Handle terminal nodes (leaf nodes with direct text)
        if (node instanceof TerminalNode) {
            return node.getText();
        }
        
        // Handle non-terminal nodes by recursively processing children
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(extractConditionText(node.getChild(i)));
        }
        return sb.toString();
    }
    
    /**
     * Parses a condition string and creates a corresponding Condition object.
     * <p>
     * This method analyzes condition text to identify comparison operators and creates
     * Condition objects for the rule engine. It handles various comparison operators
     * with precedence given to longer operators (e.g., "<=" before "<").
     * <p>
     * Supported operators (in precedence order):
     * - "<=", ">=" (compound operators first)
     * - "==", "!=" (equality operators)
     * - "<", ">" (simple comparison operators)
     * <p>
     * Example usage:
     * <pre>
     *     Condition cond1 = parseCondition("x<=5");  // Creates less-than-or-equal condition
     *     Condition cond2 = parseCondition("a==b");  // Creates equality condition
     * </pre>
     *
     * @param conditionText The condition text to parse (e.g., "x<5", "a==b")
     * @return A Condition object representing the parsed condition, or null if no operator found
     */
    private Condition parseCondition(String conditionText) {
        // Simple condition parsing - look for comparison operators
        // Order matters: check longer operators first to avoid partial matches
        String[] operators = {"<=", ">=", "==", "!=", "<", ">"};

        for (String op : operators) {
            if (conditionText.contains(op)) {
                // Split on the operator, limiting to 2 parts
                String[] parts = conditionText.split(op, 2);
                if (parts.length == 2) {
                    // Create condition object with unique identifier
                    Condition condition = new Condition();
                    condition.setId(UUID.randomUUID().toString());

                    // Set condition properties
                    condition.setConditionType(mapPythonOperatorToRamanujan(op));
                    condition.setComparisionCommand1(parts[0].trim());
                    condition.setComparisionCommand2(parts[1].trim());

                    // Register condition in rule engine input
                    ruleEngineInput.getConditions().add(condition);
                    return condition;
                }
            }
        }

        return null; // No supported operator found
    }

    /**
     * ANTLR listener method called when entering a return statement in the Python AST.
     * <p>
     * This method enforces the rule that return statements are not supported in the
     * Ramanujan system. Instead, functions should use pass-by-reference parameters
     * to communicate results back to callers.
     * <p>
     * Unsupported Python code (throws CompilationException):
     * <pre>
     *     def func():
     *         return 42  # This will throw an exception
     * </pre>
     *
     * Recommended alternative:
     * <pre>
     *     def func(result):
     *         result = 42  # Use pass-by-reference instead
     * </pre>
     *
     * @param ctx The ANTLR parse tree context for the return statement
     * @throws RuntimeException Always thrown to indicate return statements are not supported
     */
    @Override
    public void enterReturn_stmt(Python3Parser.Return_stmtContext ctx) {
        // Throw compilation exception for return statements
        throw new RuntimeException("CompilationException: 'return' statements are not supported. Functions should use pass-by-reference parameters instead.");
    }

    /**
     * Recursively checks if a parse tree node contains a function call.
     * <p>
     * This method traverses the parse tree to detect function calls, which are identified
     * by atom_expr nodes that have trailer children starting with '('. This is used to
     * enforce the rule that function calls cannot appear in assignment statements.
     * <p>
     * Example patterns detected:
     * <pre>
     *     func()           # Simple function call
     *     obj.method()     # Method call
     *     func(x, y)       # Function call with arguments
     * </pre>
     *
     * @param node The parse tree node to examine for function calls
     * @return true if the node or any of its children contains a function call, false otherwise
     */
    private boolean containsFunctionCall(org.antlr.v4.runtime.tree.ParseTree node) {
        // Check if this node is an atom_expr with a function call trailer
        if (node instanceof Python3Parser.Atom_exprContext) {
            Python3Parser.Atom_exprContext atomExpr = (Python3Parser.Atom_exprContext) node;
            // Check each trailer for function call pattern (starts with '(')
            for (Python3Parser.TrailerContext trailer : atomExpr.trailer()) {
                if (trailer.getChildCount() >= 2 && "(".equals(trailer.getChild(0).getText())) {
                    return true; // Found a function call
                }
            }
        }

        // Recursively check all children nodes
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsFunctionCall(node.getChild(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a new Array object with the specified name, data type, and dimensions.
     * <p>
     * This method generates a new Array with a unique ID and registers it in both the
     * array map and the rule engine input for later processing.
     * <p>
     * Example usage:
     * <pre>
     *     Array arr = createArray("myArray", "integer", Arrays.asList(10, 5));
     *     // Creates a 10x5 integer array
     * </pre>
     *
     * @param name       The name of the array variable
     * @param dataType   The data type of array elements (e.g., "integer", "real")
     * @param dimensions List of array dimensions
     * @return The created Array object
     */
    private Array createArray(String name, String dataType, List<Integer> dimensions) {
        Array array = new Array();
        String scopePrefix = !variableScope.isEmpty() ? variableScope.get(variableScope.size() - 1) : "";

        array.setId(scopePrefix + UUID.randomUUID().toString());
        array.setName(name);
        array.setDataType(dataType);
        array.setDimension(dimensions);

        // Register array in maps and rule engine input
        String scopedName = scopePrefix + name;
        arrayMap.put(scopedName, array);
        ruleEngineInput.getArrays().add(array);

        return array;
    }

    /**
     * Creates a command for array element assignment operations.
     * <p>
     * This method generates a Command object specifically for array assignments,
     * including the array ID, indices, and operation type.
     * <p>
     * Example usage:
     * <pre>
     *     Command cmd = createArrayAssignmentCommand(array, Arrays.asList("0", "1"), "assign");
     *     // Creates command for arr[0][1] = value assignment
     * </pre>
     *
     * @param array      The Array object being assigned to
     * @param indices    List of index expressions for multi-dimensional access
     * @param setValueCommandId  The command ID for the value being set
     * @return The created Command object for the array assignment
     */
    private Command createArrayAssignmentCommand(Array array, List<String> indices, String setValueCommandId) {
        Command command = new Command();
        command.setId(UUID.randomUUID().toString());
        ArrayCommand arrayCommand = new ArrayCommand();
        arrayCommand.setArrayId(array.getId());


        // Set array indices for multi-dimensional access
        if (indices != null && !indices.isEmpty()) {
            arrayCommand.setIndex(indices);
        }

        Command leftCommand = new Command();
        leftCommand.setId(UUID.randomUUID().toString());
        leftCommand.setArrayCommand(arrayCommand);
        // Add intermediate command without participating in nextId chaining
        addCommand(leftCommand, false);

        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType(OperatorType.ASSIGN.getOperatorCode());
        operation.setOperand1(leftCommand.getId());
        operation.setOperand2(setValueCommandId);
        ruleEngineInput.getOperations().add(operation);
        command.setOperation(operation.getId());

        // Add final assignment command with nextId chaining
        addCommand(command, true);

        return command;
    }

    /**
     * Checks if a variable name represents an array access pattern.
     * <p>
     * This method detects array access syntax like "arr[0]", "matrix[i][j]", etc.
     * <p>
     * Example usage:
     * <pre>
     *     boolean isArray = isArrayAccess("data[5]");      // Returns true
     *     boolean isVar = isArrayAccess("simpleVar");      // Returns false
     * </pre>
     *
     * @param varName The variable name to check for array access pattern
     * @return true if the name contains array access brackets, false otherwise
     */
    private boolean isArrayAccess(String varName) {
        return varName.contains("[") && varName.contains("]");
    }

    /**
     * Extracts the base array name from an array access expression.
     * <p>
     * This method parses array access syntax to get the root array name.
     * <p>
     * Example usage:
     * <pre>
     *     String arrayName = extractArrayName("data[5][2]"); // Returns "data"
     *     String arrayName2 = extractArrayName("matrix[i]"); // Returns "matrix"
     * </pre>
     *
     * @param arrayAccess The full array access expression
     * @return The base array name without indices
     */
    private String extractArrayName(String arrayAccess) {
        int bracketIndex = arrayAccess.indexOf('[');
        return bracketIndex > 0 ? arrayAccess.substring(0, bracketIndex) : arrayAccess;
    }

    /**
     * Extracts array indices from an array access expression.
     * <p>
     * This method parses multi-dimensional array access to extract all index expressions.
     * <p>
     * Example usage:
     * <pre>
     *     List&lt;String&gt; indices = extractArrayIndices("data[5][i+1]");
     *     // Returns ["5", "i+1"]
     * </pre>
     *
     * @param arrayAccess The full array access expression
     * @return List of index expressions, empty list if no indices found
     */
    private List<String> extractArrayIndices(String arrayAccess) {
        List<String> indices = new ArrayList<>();
        int start = arrayAccess.indexOf('[');

        while (start != -1) {
            int end = arrayAccess.indexOf(']', start);
            if (end != -1) {
                String index = arrayAccess.substring(start + 1, end).trim();
                indices.add(index);
                start = arrayAccess.indexOf('[', end);
            } else {
                break;
            }
        }

        return indices;
    }

    /**
     * Checks if a string represents a potential array declaration (starts with '[').
     * <p>
     * This is a preliminary check to identify expressions that might be array declarations.
     * Further validation is performed by validateArrayDeclarationFormat().
     * <p>
     * Example usage:
     * <pre>
     *     boolean isArray = isArrayDeclaration("[0 for _ in range(10)]"); // Returns true
     *     boolean isVar = isArrayDeclaration("42");                       // Returns false
     * </pre>
     *
     * @param rightSide The right side of an assignment expression
     * @return true if the expression starts with '[', indicating potential array declaration
     */
    private boolean isArrayDeclaration(String rightSide) {
        return rightSide.trim().startsWith("[") && rightSide.trim().endsWith("]");
    }

    /**
     * Validates array declaration format and extracts dimension information.
     * <p>
     * This method enforces the strict array declaration syntax by pattern matching
     * against the required n-dimensional list comprehension formats. It extracts
     * dimension sizes and determines the data type from the initial value.
     * <p>
     * Valid patterns recognized:
     * <pre>
     *     [value for _ in range(dim)]                           # 1D
     *     [[value] * dim2 for _ in range(dim1)]                 # 2D
     *     [[[value] * dim3 for _ in range(dim2)] for _ in range(dim1)]  # 3D
     *     # And so on for higher dimensions...
     * </pre>
     *
     * @param declaration The array declaration string to validate
     * @return ArrayDeclarationInfo containing dimensions and data type, or null if invalid
     */
    private ArrayDeclarationInfo validateArrayDeclarationFormat(String declaration) {
        declaration = declaration.trim();

        // Remove outer brackets
        if (!declaration.startsWith("[") || !declaration.endsWith("]")) {
            return null;
        }

        String content = declaration.substring(1, declaration.length() - 1).trim();

        // Check for 1D array: [value for _ in range(dim)]
        if (content.matches(".*\\s+for\\s+_\\s+in\\s+range\\s*\\(.*\\).*")) {
            return validate1DArrayFormat(content);
        }

        // Check for 2D+ array: [[...] * dim for _ in range(dim)] pattern
        if (content.startsWith("[") && content.contains(" for _ in range(")) {
            return validateMultiDimensionalArrayFormat(content);
        }

        // If it doesn't match the required patterns, it's invalid
        return null;
    }

    /**
     * Validates 1D array declaration format.
     * <p>
     * Expected format: [value for _ in range(dimension)]
     *
     * @param content The content inside the outer brackets
     * @return ArrayDeclarationInfo for 1D array, or null if invalid
     */
    private ArrayDeclarationInfo validate1DArrayFormat(String content) {
        // Pattern: value for _ in range(dimension)
        String[] parts = content.split("\\s+for\\s+_\\s+in\\s+range\\s*\\(");
        if (parts.length != 2) {
            return null;
        }

        String valueStr = parts[0].trim();
        String rangePart = parts[1].trim();

        // Extract dimension from range(dimension)
        if (!rangePart.endsWith(")")) {
            return null;
        }

        String dimensionStr = rangePart.substring(0, rangePart.length() - 1).trim();

        try {
            // Try to parse dimension as integer
            int dimension = Integer.parseInt(dimensionStr);
            if (dimension <= 0) {
                throw new RuntimeException("CompilationException: Invalid array dimension '" + dimension + "'. Must be a positive integer.");
            }

            // Determine data type from initial value
            String dataType = determineDataType(valueStr);

            return new ArrayDeclarationInfo(Collections.singletonList(dimension), dataType, Collections.singletonList(dimensionStr), false);
        } catch (NumberFormatException e) {
            // Dimension is a variable - support variable dimensions
            // Determine data type from initial value
            String dataType = determineDataType(valueStr);
            
            // Check if variable exists in scope
            Variable dimVar = getVariable(variableMap, dimensionStr, variableScope);
            if (dimVar == null) {
                throw new RuntimeException("CompilationException: Variable dimension '" + dimensionStr + "' not found in scope.");
            }

            return new ArrayDeclarationInfo(Collections.singletonList(1), dataType, Collections.singletonList(dimensionStr), true);
        }
    }

    /**
     * Validates multi-dimensional array declaration format.
     * <p>
     * Expected formats:
     * 2D: [[value] * dim2 for _ in range(dim1)]  → dimensions [dim1, dim2]
     * 3D: [[[value] * dim3 for _ in range(dim2)] for _ in range(dim1)]  → dimensions [dim1, dim2, dim3]
     * etc.
     *
     * @param content The content inside the outer brackets
     * @return ArrayDeclarationInfo for multi-dimensional array, or null if invalid
     */
    private ArrayDeclarationInfo validateMultiDimensionalArrayFormat(String content) {
        List<Integer> dimensions = new ArrayList<>();
        List<String> dimensionExpressions = new ArrayList<>();
        List<String> tempDimensions = new ArrayList<>(); // Collect dimensions in reverse order
        String currentContent = content;
        String dataType = "integer"; // default
        boolean hasVariableDimensions = false;

        // Process nested structure from outside to inside, collecting dimensions
        // For [[[0] * z for _ in range(y)] for _ in range(x)], we want dimensions [x, y, z]
        while (currentContent.startsWith("[") && currentContent.contains(" for _ in range(")) {
            // Find the last (outermost) "] for _ in range(" pattern
            int forIndex = currentContent.lastIndexOf(" for _ in range(");
            if (forIndex == -1) break;

            // Extract the range part
            int rangeStart = currentContent.indexOf("(", forIndex);
            int rangeEnd = currentContent.indexOf(")", rangeStart);
            if (rangeStart == -1 || rangeEnd == -1) {
                return null;
            }

            String dimensionStr = currentContent.substring(rangeStart + 1, rangeEnd).trim();
            // Collect dimension expressions (we'll reverse them later)
            tempDimensions.add(dimensionStr);

            // Extract the inner part before " for _ in range("
            String beforeFor = currentContent.substring(0, forIndex).trim();
            if (!beforeFor.startsWith("[")) {
                return null;
            }

            currentContent = beforeFor.substring(1).trim(); // Remove the opening bracket
        }

        // Now check for multiplication pattern: [...] * dim at the innermost level
        if (currentContent.contains("] * ")) {
            String[] mulParts = currentContent.split("\\]\\s*\\*\\s*");
            if (mulParts.length == 2) {
                String innerDimStr = mulParts[1].trim();
                // Add inner dimension to the temp list
                tempDimensions.add(innerDimStr);
                currentContent = mulParts[0].trim() + "]"; // Continue with the inner part
            }
        }

        // Now process the tempDimensions in the order we collected them (don't reverse!)
        // This gives us the correct order: outer dimensions first, inner dimensions last
        for (String dimStr : tempDimensions) {
            try {
                int dimension = Integer.parseInt(dimStr);
                if (dimension <= 0) {
                    return null;
                }
                dimensions.add(dimension);
                dimensionExpressions.add(dimStr);
            } catch (NumberFormatException e) {
                // Handle variable dimensions
                Variable dimVar = getVariable(variableMap, dimStr, variableScope);
                if (dimVar == null) {
                    throw new RuntimeException("CompilationException: Variable dimension '" + dimStr + "' not found in scope.");
                }
                hasVariableDimensions = true;
                dimensions.add(1); // placeholder
                dimensionExpressions.add(dimStr);
            }
        }

        // Extract the final value to determine data type
        if (currentContent.startsWith("[") && currentContent.endsWith("]")) {
            String finalValue = currentContent.substring(1, currentContent.length() - 1).trim();
            dataType = determineDataType(finalValue);
        }

        return dimensions.size() >= 2 ? new ArrayDeclarationInfo(dimensions, dataType, dimensionExpressions, hasVariableDimensions) : null;
    }

    /**
     * Determines the data type from an initial value string.
     * <p>
     * This method analyzes the initial value to determine the appropriate data type
     * for the array elements.
     *
     * @param valueStr The initial value string
     * @return The determined data type ("integer", "real", or "string")
     */
    private String determineDataType(String valueStr) {
        valueStr = valueStr.trim();

        try {
            Integer.parseInt(valueStr);
            return "integer";
        } catch (NumberFormatException e1) {
            try {
                Double.parseDouble(valueStr);
                return "real";
            } catch (NumberFormatException e2) {
                return "string";
            }
        }
    }

    /**
     * Helper class to hold array declaration information.
     */
    private static class ArrayDeclarationInfo {
        final List<Integer> dimensions;
        final String dataType;
        final List<String> dimensionExpressions;
        final boolean hasVariableDimensions;

        ArrayDeclarationInfo(List<Integer> dimensions, String dataType, List<String> dimensionExpressions, boolean hasVariableDimensions) {
            this.dimensions = dimensions;
            this.dataType = dataType;
            this.dimensionExpressions = dimensionExpressions;
            this.hasVariableDimensions = hasVariableDimensions;
        }
    }
}



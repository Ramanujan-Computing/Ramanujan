package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayCommand;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.ClassDefinition;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;

import java.util.*;

/**
 * Converts Python AST nodes into RuleEngineInput structures.
 * 
 * <p>This converter is the bridge between Python's abstract syntax tree representation
 * and Ramanujan's internal RuleEngineInput format. It traverses AST nodes (ModuleNode,
 * AssignNode, IfNode, etc.) and generates corresponding RuleEngine structures (Variables,
 * Operations, Conditions, Commands, etc.) that can be executed by the Ramanujan engine.</p>
 * 
 * <h3>Conversion Flow</h3>
 * <pre>
 * Python Code
 *     ↓ (python3 -m ast)
 * Python AST Dump
 *     ↓ (AstParser)
 * AST Node Objects (ModuleNode, AssignNode, etc.)
 *     ↓ (PythonAstToRuleEngineInputConverter - THIS CLASS)
 * RuleEngineInput (Variables, Operations, Commands, etc.)
 *     ↓ (Ramanujan Engine)
 * Execution
 * </pre>
 * 
 * <h3>Key Responsibilities</h3>
 * <ul>
 *   <li><b>Statement Conversion:</b> Convert Python statements (assignments, if/while, function calls)
 *       into Command objects with appropriate links and references</li>
 *   <li><b>Expression Conversion:</b> Convert Python expressions (binary operations, function calls,
 *       array subscripts) into Operation objects and return their IDs</li>
 *   <li><b>Type Inference:</b> Determine variable data types from their initial values
 *       (Integer, Double, String, array)</li>
 *   <li><b>Scope Management:</b> Track variable scope through nested blocks (if/while/function)
 *       using variableScope list</li>
 *   <li><b>Command Chaining:</b> Link commands in sequence using nextId references</li>
 *   <li><b>Debug Code Generation:</b> Maintain human-readable Python code representation
 *       via DebugLevelCodeCreator</li>
 * </ul>
 * 
 * <h3>Conversion Examples</h3>
 * 
 * <h4>Example 1: Simple Assignment</h4>
 * <pre>
 * Python Code:
 *   x = 5
 * 
 * AST:
 *   AssignNode(targets=[NameNode('x')], value=ConstantNode(5))
 * 
 * Converts To:
 *   - Variable: {id: "var_uuid", name: "x", dataType: "Integer"}
 *   - Constant: {id: "const_uuid", value: "5", dataType: "Integer"}
 *   - Operation: {id: "op_uuid", operatorType: "=", operand1: "var_uuid", operand2: "const_uuid"}
 *   - Command: {id: "cmd_uuid", operationId: "op_uuid"}
 * </pre>
 * 
 * <h4>Example 2: Binary Operation</h4>
 * <pre>
 * Python Code:
 *   result = x + 5
 * 
 * AST:
 *   AssignNode(
 *     targets=[NameNode('result')],
 *     value=BinOpNode(left=NameNode('x'), op='Add', right=ConstantNode(5)))
 * 
 * Converts To:
 *   - Variable: {id: "result_var_id", name: "result", dataType: "Double"}
 *   - Constant: {id: "const_5_id", value: "5"}
 *   - Operation (addition): {id: "add_op_id", operatorType: "+", 
 *                           operand1: "x_var_id", operand2: "const_5_id"}
 *   - Operation (assignment): {id: "assign_op_id", operatorType: "=",
 *                             operand1: "result_var_id", operand2: "add_op_id"}
 *   - Command: {id: "cmd_id", operationId: "assign_op_id"}
 * </pre>
 * 
 * <h4>Example 3: If Statement</h4>
 * <pre>
 * Python Code:
 *   if x > 5:
 *       y = 10
 *   else:
 *       y = 20
 * 
 * AST:
 *   IfNode(
 *     test=CompareNode(left=NameNode('x'), ops=['Gt'], comparators=[ConstantNode(5)]),
 *     body=[AssignNode(targets=[NameNode('y')], value=ConstantNode(10))],
 *     orelse=[AssignNode(targets=[NameNode('y')], value=ConstantNode(20))]))
 * 
 * Converts To:
 *   - Condition: {id: "cond_id", conditionType: ">", 
 *                comparisionCommand1: "x_var_id", comparisionCommand2: "const_5_id"}
 *   - If Block: {id: "if_id", conditionId: "cond_id", 
 *               ifCommand: "cmd_y_10_id", elseCommandId: "cmd_y_20_id"}
 *   - Command (if body): {id: "cmd_y_10_id", operationId: "op_y_10_id"}
 *   - Command (else body): {id: "cmd_y_20_id", operationId: "op_y_20_id"}
 *   - Command (if statement): {id: "cmd_if_id", ifBlocks: "if_id"}
 * </pre>
 * 
 * <h4>Example 4: Array Assignment</h4>
 * <pre>
 * Python Code:
 *   arr = [1, 2, 3]
 *   arr[0] = 10
 * 
 * AST:
 *   AssignNode(targets=[NameNode('arr')], value=ListNode(elts=[Constant(1), Constant(2), Constant(3)]))
 *   AssignNode(
 *     targets=[SubscriptNode(value=NameNode('arr'), slice=ConstantNode(0))],
 *     value=ConstantNode(10))
 * 
 * Converts To:
 *   - Array: {id: "arr_id", name: "arr", dataType: "array", dimension: [3]}
 *   - Command (array creation): {id: "cmd_arr_id", arrayCommand: "arr_id"}
 *   - Command (array assignment): {id: "cmd_arr_assign_id", ...}
 * </pre>
 * 
 * <h3>Data Structures</h3>
 * <table border="1">
 *   <tr><th>Input (AST)</th><th>Output (RuleEngine)</th><th>Purpose</th></tr>
 *   <tr><td>AssignNode</td><td>Variable + Operation + Command</td><td>Variable declaration/assignment</td></tr>
 *   <tr><td>AugAssignNode</td><td>Operation + Command</td><td>Augmented assignment (+=, -=, etc.)</td></tr>
 *   <tr><td>IfNode</td><td>If + Condition + Commands</td><td>Conditional branching</td></tr>
 *   <tr><td>WhileNode</td><td>While + Condition + Commands</td><td>Loop structure</td></tr>
 *   <tr><td>BinOpNode</td><td>Operation</td><td>Binary arithmetic/logic</td></tr>
 *   <tr><td>CompareNode</td><td>Condition</td><td>Comparison operations</td></tr>
 *   <tr><td>ConstantNode</td><td>Constant</td><td>Literal values</td></tr>
 *   <tr><td>NameNode</td><td>Variable/Array ID</td><td>Variable references</td></tr>
 *   <tr><td>ListNode</td><td>Array</td><td>Array/list creation</td></tr>
 *   <tr><td>SubscriptNode</td><td>Array reference</td><td>Array element access</td></tr>
 *   <tr><td>CallNode</td><td>FunctionCall</td><td>Function invocation</td></tr>
 * </table>
 * 
 * <h3>Type Inference Rules</h3>
 * <ul>
 *   <li><b>Integer:</b> ConstantNode with Integer value → Variable with dataType="Integer"</li>
 *   <li><b>Double:</b> ConstantNode with Double value OR BinOpNode → Variable with dataType="Double"</li>
 *   <li><b>String:</b> ConstantNode with String value → Variable with dataType="String"</li>
 *   <li><b>Array:</b> ListNode → Array with dataType="array"</li>
 * </ul>
 * 
 * <h3>Scope Management</h3>
 * <p>Variables can be declared at different scopes:</p>
 * <pre>
 * Global Scope: variableScope = []
 *   x = 5           # Stored as: "x" → Variable
 *   
 * If Block Scope: variableScope = ["if_id_123"]
 *   if condition:
 *       y = 10      # Stored as: "if_id_123y" → Variable
 *       
 * While Block Scope: variableScope = ["while_id_456"]
 *   while condition:
 *       z = 20      # Stored as: "while_id_456z" → Variable
 *       
 * Nested Scope: variableScope = ["if_id_123", "while_id_789"]
 *   if condition:
 *       while nested:
 *           w = 30  # Stored as: "while_id_789w" → Variable
 * </pre>
 * 
 * <h3>Command Chaining</h3>
 * <p>Commands are linked in execution order:</p>
 * <pre>
 * Python:
 *   x = 5
 *   y = 10
 *   z = x + y
 * 
 * Commands:
 *   Command1 {id: "cmd1", operationId: "op1", nextId: "cmd2"}
 *   Command2 {id: "cmd2", operationId: "op2", nextId: "cmd3"}
 *   Command3 {id: "cmd3", operationId: "op3", nextId: null}
 * </pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * // Setup
 * CodeConverter codeConverter = new CodeConverter();
 * RuleEngineInput ruleEngineInput = new RuleEngineInput();
 * DebugLevelCodeCreator debugCreator = new DebugLevelCodeCreator();
 * Map<Integer, RuleEngineInputUnits> functionFrameMap = new HashMap<>();
 * Integer[] frameCounter = {0};
 * 
 * // Create converter
 * PythonAstToRuleEngineInputConverter converter = new PythonAstToRuleEngineInputConverter(
 *     codeConverter, ruleEngineInput, debugCreator, functionFrameMap, frameCounter);
 * 
 * // Parse Python code to AST
 * String pythonCode = "x = 5\ny = x + 10";
 * ModuleNode module = parseToAst(pythonCode);
 * 
 * // Convert AST to RuleEngineInput
 * List<String> variableScope = new ArrayList<>();
 * List<Command> commands = converter.convert(module, variableScope);
 * 
 * // Result: ruleEngineInput now contains:
 * // - Variables: x, y
 * // - Constants: 5, 10
 * // - Operations: assignment, addition
 * // - Commands: linked chain of commands
 * </pre>
 * 
 * @see ModuleNode
 * @see AstNode
 * @see RuleEngineInput
 * @see Command
 * @see Operation
 * @see Variable
 * @see Condition
 */
public class PythonAstToRuleEngineInputConverter {
    
    /**
     * CodeConverter instance managing variable/array/method argument maps.
     * Used to lookup existing variables and register new ones in appropriate scopes.
     */
    private CodeConverter codeConverter;
    
    /**
     * The target RuleEngineInput object being populated with converted structures.
     * Contains lists of Variables, Arrays, Operations, Commands, Conditions, If/While blocks, etc.
     */
    private RuleEngineInput ruleEngineInput;
    
    /**
     * Creates human-readable Python code representation during conversion.
     * Maintains indentation and generates debug output matching original Python syntax.
     */
    private DebugLevelCodeCreator debugLevelCodeCreator;
    
    /**
     * Maps function frame IDs to their local variables/arguments.
     * Used for function-scoped variable management (currently minimal function support).
     */
    private Map<Integer, RuleEngineInputUnits> functionFrameVariableMap;
    
    /**
     * Counter for generating unique function frame IDs.
     * Array of size 1 to allow pass-by-reference modification.
     */
    private Integer[] frameVariableCounterId;
    
    /**
     * Tracks inferred data types for variables.
     * Maps variable name → data type ("Integer", "Double", "String", "array").
     * Used to remember type decisions made during first assignment.
     */
    private Map<String, String> inferredTypes = new HashMap<>();
    
    /**
     * Maps function name to the list of argument IDs in order (original args + return target args).
     * Used in convertReturn to retrieve return target parameter IDs.
     */
    private Map<String, List<String>> functionDefinitionArgs = new HashMap<>();
    
    /**
     * Maps function name to the number of return values it returns.
     * Used to add extra parameters to function definitions for return targets.
     */
    private Map<String, Integer> functionReturnCounts = new HashMap<>();
    
    /**
     * Tracks the current function being defined (name).
     * Used to associate return statements with their function.
     */
    private String currentFunctionName;

    /**
     * All top-level {@link FunctionDefNode}s in the module, keyed by function name.
     * Populated by a pre-scan at the start of {@link #convert(ModuleNode, List)} so that
     * GPU functions can look up helper functions regardless of definition order.
     */
    private Map<String, FunctionDefNode> allModuleFunctions = new HashMap<>();

    /** Maps class name → compile-time class metadata (field order, indices). */
    private Map<String, ClassMeta> classRegistry = new HashMap<>();

    /** Set to the class name while compiling a class method body; null otherwise. */
    private String currentClassName = null;

    /** Compile-time metadata about a class: its ordered field names and index maps. */
    private static class ClassMeta {
        String className;
        List<String> scalarFieldNames = new ArrayList<>();
        List<String> arrayFieldNames = new ArrayList<>();
        Map<String, Integer> scalarFieldIndex = new HashMap<>();
        Map<String, Integer> arrayFieldIndex = new HashMap<>();
    }
    
    /**
     * Constructs a new converter for transforming Python AST to RuleEngineInput.
     * 
     * @param codeConverter The code converter managing variable/array maps and scope resolution
     * @param ruleEngineInput The target RuleEngineInput to populate with converted structures
     * @param debugLevelCodeCreator Generator for human-readable debug code
     * @param functionFrameVariableMap Map of function frames to their local variables
     * @param frameVariableCounterId Counter for unique function frame ID generation
     */
    public PythonAstToRuleEngineInputConverter(CodeConverter codeConverter,
                                                RuleEngineInput ruleEngineInput,
                                                DebugLevelCodeCreator debugLevelCodeCreator,
                                                Map<Integer, RuleEngineInputUnits> functionFrameVariableMap,
                                                Integer[] frameVariableCounterId) {
        this.codeConverter = codeConverter;
        this.ruleEngineInput = ruleEngineInput;
        this.debugLevelCodeCreator = debugLevelCodeCreator;
        this.functionFrameVariableMap = functionFrameVariableMap;
        this.frameVariableCounterId = frameVariableCounterId;
    }
    
    /**
     * Converts a Python module (top-level AST node) into a list of Commands.
     * 
     * <p>This is the main entry point for conversion. It processes all statements in the
     * module's body sequentially, converting each to a Command and linking them via nextId
     * references to maintain execution order.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python Module:
     *   x = 5
     *   y = 10
     *   z = x + y
     * 
     * ModuleNode:
     *   body: [
     *     AssignNode(targets=[Name('x')], value=Constant(5)),
     *     AssignNode(targets=[Name('y')], value=Constant(10)),
     *     AssignNode(targets=[Name('z')], value=BinOp(Name('x'), Add, Name('y')))
     *   ]
     * 
     * Returns:
     *   [
     *     Command1 {id: "cmd1", operationId: "op1", nextId: "cmd2"},
     *     Command2 {id: "cmd2", operationId: "op2", nextId: "cmd3"},
     *     Command3 {id: "cmd3", operationId: "op3", nextId: null}
     *   ]
     * </pre>
     * 
     * @param module The ModuleNode containing the Python program's AST
     * @param variableScope List of scope IDs for nested block tracking (empty for top-level)
     * @return List of Commands in execution order, linked via nextId references
     * @throws CompilationException If conversion fails due to unsupported constructs or errors
     */
    public List<Command> convert(ModuleNode module, List<String> variableScope) throws CompilationException {
        List<Command> commands = new ArrayList<>();
        Command previousCommand = null;
        List<AstNode> nonFunctionDefNodes = new ArrayList<>();
        List<AstNode> classDefNodes = new ArrayList<>();
        List<AstNode> functionDefNodes = new ArrayList<>();

        // Pre-scan: collect every function definition so GPU converters can look up helpers.
        for (AstNode node : module.getBody()) {
            if (node instanceof FunctionDefNode) {
                FunctionDefNode fdn = (FunctionDefNode) node;
                allModuleFunctions.put(fdn.getName(), fdn);
            }
        }

        for (AstNode node : module.getBody()) {
            if (node instanceof FunctionDefNode) {
                functionDefNodes.add(node);
            } else if (node instanceof ClassDefNode) {
                classDefNodes.add(node);
            } else {
                nonFunctionDefNodes.add(node);
            }
        }

        // 1a. Pre-register global scalars so class-method bodies can reference them.
        //     (convertAssign will find them via getExistingVariable and skip re-registration.)
        preRegisterGlobalScalars(nonFunctionDefNodes, variableScope);

        // 1b. Class definitions — must run first so classRegistry is populated before
        //    any instantiation statements (e.g. c1 = Counter()) are processed.
        //    convertClassDef emits only IR metadata (ClassDefinition, Variable/Array,
        //    FunctionCall defs), never Command objects, so this is safe.
        for (AstNode node : classDefNodes) {
            convertClassDef((ClassDefNode) node, variableScope);
        }

        // 2. Non-function/non-class top-level statements
        for (AstNode node : nonFunctionDefNodes) {
            Command command = convertStatement(node, variableScope, null, null, null);
            if (command != null) {
                commands.add(command);
                if (previousCommand != null) {
                    previousCommand.setNextId(command.getId());
                }
                previousCommand = command;
            }
        }

        // 3. Standalone function definitions
        for (AstNode node : functionDefNodes) {
            convertStatement(node, variableScope, null, null, null);
        }

        return commands;
    }
    
    /**
     * Pre-registers global scalar variables so class-method bodies can reference them
     * before the actual assignment commands are emitted in the main pass.
     */
    private void preRegisterGlobalScalars(List<AstNode> nodes, List<String> variableScope) {
        for (AstNode node : nodes) {
            if (!(node instanceof AssignNode)) continue;
            AssignNode assign = (AssignNode) node;
            if (assign.getTargets().isEmpty()) continue;
            AstNode target = assign.getTargets().get(0);
            if (!(target instanceof NameNode)) continue;
            AstNode value = assign.getValue();
            String dataType = inferDataType(value);
            if ("array".equals(dataType)) continue;
            String varName = ((NameNode) target).getId();
            if (codeConverter.getVariableMap().get(varName) != null) continue;
            Variable variable = new Variable();
            variable.setId(UUID.randomUUID().toString() + "_name_" + varName);
            variable.setName(varName);
            variable.setDataType(dataType);
            ruleEngineInput.getVariables().add(variable);
            codeConverter.setVariable(variable, "");
        }
    }

    /**
     * Converts a single AST statement node into a Command.
     * 
     * <p>Routes the statement to the appropriate conversion method based on its type.
     * Each statement type has specific conversion logic that populates the Command object
     * with the appropriate references (operationId, ifBlocks, whileBlock, etc.).</p>
     * 
     * <h4>Statement Type Routing</h4>
     * <ul>
     *   <li><b>AssignNode</b> → convertAssign() - Variable/array assignments</li>
     *   <li><b>AugAssignNode</b> → convertAugAssign() - Augmented assignments (+=, -=, etc.)</li>
     *   <li><b>IfNode</b> → convertIf() - Conditional branching</li>
     *   <li><b>WhileNode</b> → convertWhile() - Loop structures</li>
     *   <li><b>FunctionDefNode</b> → convertFunctionDef() - Function definitions (returns null)</li>
     *   <li><b>ExprNode</b> → convertExpr() - Expression statements (function calls)</li>
     *   <li><b>ReturnNode</b> → Returns null (skip in main flow)</li>
     * </ul>
     * 
     * <h4>Command Creation</h4>
     * <pre>
     * Each command receives:
     *   - Unique ID: "command_" + UUID
     *   - Code pointer: Line number in debug output
     *   - Type-specific reference: operationId, ifBlocks, whileBlock, arrayCommand, etc.
     * </pre>
     * 
     * @param node The statement node to convert
     * @param variableScope Current scope stack for variable resolution
     * @param variableFrameMap Map to track variables/arrays created in function body (null for non-function contexts)
     * @param counter Array containing current frame counter (modified during conversion)
     * @param parentScopeUnit The parent scope unit (If/While/FunctionCall) for this command
     * @return The created Command, or null if statement doesn't produce a command (functions, returns)
     * @throws CompilationException If conversion fails for unsupported constructs
     */
    private Command convertStatement(AstNode node, List<String> variableScope,
                                    Map<Integer, RuleEngineInputUnits> variableFrameMap,
                                    int[] counter,
                                    RuleEngineInputUnits parentScopeUnit) throws CompilationException {
        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        command.setCodeStrPtr(debugLevelCodeCreator.getLine());
        
        // Set parent relationship for this command
        if (parentScopeUnit != null) {
            command.setImmediateParentRuleEngineInputUnitId(parentScopeUnit.getId());
        }
        
        if (node instanceof AssignNode) {
            convertAssign((AssignNode) node, command, variableScope, variableFrameMap, counter);
        } else if (node instanceof AugAssignNode) {
            convertAugAssign((AugAssignNode) node, command, variableScope);
        } else if (node instanceof IfNode) {
            convertIf((IfNode) node, command, variableScope, variableFrameMap, counter, parentScopeUnit);
        } else if (node instanceof WhileNode) {
            convertWhile((WhileNode) node, command, variableScope, variableFrameMap, counter, parentScopeUnit);
        } else if (node instanceof FunctionDefNode) {
            convertFunctionDef((FunctionDefNode) node, command, variableScope, parentScopeUnit);
            return null; // Functions don't create commands in main flow
        } else if (node instanceof ExprNode) {
            convertExpr((ExprNode) node, command, variableScope);
        } else if (node instanceof ReturnNode) {
            return convertReturn((ReturnNode) node, variableScope, parentScopeUnit);
        } else if (node instanceof DeleteNode) {
            convertDelete((DeleteNode) node, command, variableScope);
        } else if (node instanceof ClassDefNode) {
            convertClassDef((ClassDefNode) node, variableScope);
            return null;
        }
        ruleEngineInput.getCommands().add(command);
        return command;
    }
    
    /**
     * Converts an assignment statement into Variable/Array and Operation structures.
     * 
     * <p>Handles both variable assignments and array element assignments. For new variables,
     * infers the data type from the value being assigned. For existing variables, creates
     * an Operation to perform the assignment.</p>
     * 
     * <h4>Variable Assignment (New Variable)</h4>
     * <pre>
     * Python: x = 5
     * AST: AssignNode(targets=[NameNode('x')], value=ConstantNode(5))
     * 
     * Creates:
     *   - Variable {id: "var_uuid", name: "x", dataType: "Integer"}
     *   - Constant {id: "const_uuid", value: "5", dataType: "Integer"}
     *   - Operation {id: "op_uuid", operatorType: "=", operand1: "var_uuid", operand2: "const_uuid"}
     *   - Command.operationId = "op_uuid"
     * </pre>
     * 
     * <h4>Variable Assignment (Existing Variable)</h4>
     * <pre>
     * Python: x = 10  (x already declared)
     * 
     * Creates:
     *   - Constant {id: "const_uuid", value: "10"}
     *   - Operation {id: "op_uuid", operatorType: "=", operand1: "existing_x_id", operand2: "const_uuid"}
     *   - Command.operationId = "op_uuid"
     * </pre>
     * 
     * <h4>Array Creation</h4>
     * <pre>
     * Python: arr = [1, 2, 3]
     * AST: AssignNode(targets=[NameNode('arr')], value=ListNode(elts=[...]))
     * 
     * Creates:
     *   - Array {id: "arr_uuid", name: "arr", dataType: "array", dimension: [3]}
     *   - Command.arrayCommand = "arr_uuid"
     * </pre>
     * 
     * <h4>Array Element Assignment</h4>
     * <pre>
     * Python: arr[0] = 10
     * AST: AssignNode(targets=[SubscriptNode(...)], value=ConstantNode(10))
     * 
     * Routes to: convertArrayAssignment()
     * </pre>
     * 
     * <h4>Function Call Assignment (Return Value)</h4>
     * <pre>
     * Python: result = calculate(x, y)
     * AST: AssignNode(targets=[NameNode('result')], value=CallNode(...))
     * 
     * Creates:
     *   - Variable {id: "result_var_id", name: "result", dataType: "Double"}
     *   - FunctionCall {
     *       id: "calculate",
     *       arguments: [x_id, y_id, result_var_id]  // target variable added as return argument
     *     }
     *   - Command.functionCall = functionCall
     * 
     * Return Mechanism:
     *   - Target variable is passed as additional argument to function
     *   - Function assigns return value directly to target variable
     *   - Supports single return values only (use tuple unpacking for multiple returns)
     *   - Default return type: "Double"
     * 
     * Note: Arrays cannot be assigned from function call results directly
     * </pre>
     * 
     * @param assign The AssignNode to convert
     * @param command The Command object to populate
     * @param variableScope Current scope stack for variable registration
     * @param variableFrameMap Map to track variables/arrays created in function body (null for non-function contexts)
     * @param counter Array containing current frame counter (modified when new variables/arrays are created)
     * @throws CompilationException If target or value cannot be converted
     */
    private void convertAssign(AssignNode assign, Command command, List<String> variableScope,
                              Map<Integer, RuleEngineInputUnits> variableFrameMap,
                              int[] counter) 
            throws CompilationException {
        
        AstNode target = assign.getTargets().get(0);
        AstNode value = assign.getValue();
        
        if (target instanceof NameNode) {
            NameNode nameNode = (NameNode) target;
            String varName = nameNode.getId();

            // Check for object instantiation: obj = MyClass()
            if (value instanceof CallNode) {
                CallNode callNode = (CallNode) value;
                if (callNode.getFunc() instanceof NameNode) {
                    String calledName = ((NameNode) callNode.getFunc()).getId();
                    if (classRegistry.containsKey(calledName)) {
                        String objectHandleId = UUID.randomUUID().toString();
                        Command.NewObjectCommand noc = new Command.NewObjectCommand();
                        noc.setClassName(calledName);
                        noc.setObjectHandleId(objectHandleId);
                        command.setNewObjectCommand(noc);
                        codeConverter.registerObject(varName, objectHandleId, calledName);
                        debugLevelCodeCreator.concat(varName + " = " + calledName + "()");
                        debugLevelCodeCreator.nextLine();
                        return;
                    }
                }
            }

            Variable existingVar = getExistingVariable(varName, variableScope);
            Array existingArray = getExistingArray(varName, variableScope);
            MethodDataTypeAgnosticArg existingMethodArg = getExistingMethodArg(varName, variableScope);
            
            // If it's a method arg, convert it to Variable or Array based on usage
            if (existingMethodArg != null) {
                String dataType = inferDataType(value);
                
                if ("array".equals(dataType)) {
                    // Convert MethodArg to Array
                    ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(existingMethodArg);
                    Array array = new Array();
                    array.setId(existingMethodArg.getId());
                    array.setName(existingMethodArg.getName());
                    array.setDataType("array");
                    
                    ruleEngineInput.getArrays().add(array);
                    String scope = getScope(variableScope);
                    codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + varName);
                    codeConverter.setArray(array, scope);
                    
                    Operation operation = createAssignmentOperation(array.getId(), value, variableScope);
                    command.setOperation(operation.getId());
                } else {
                    // Convert MethodArg to Variable
                    ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(existingMethodArg);
                    Variable variable = new Variable();
                    variable.setId(existingMethodArg.getId());
                    variable.setName(existingMethodArg.getName());
                    variable.setDataType(dataType);
                    
                    ruleEngineInput.getVariables().add(variable);
                    String scope = getScope(variableScope);
                    codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + varName);
                    codeConverter.setVariable(variable, scope);
                    
                    // Check if value is a function call
                    if (value instanceof CallNode) {
                        FunctionCall functionCall = convertFunctionCallWithReturnTargets(
                            (CallNode) value, variableScope, Arrays.asList(variable.getId()));
                        command.setFunctionCall(functionCall);
                    } else {
                        Operation operation = createAssignmentOperation(variable.getId(), value, variableScope);
                        command.setOperation(operation.getId());
                    }
                }
                
                debugLevelCodeCreator.concat(varName + " = ");
                appendValueToDebug(value);
                debugLevelCodeCreator.nextLine();
                return;
            }
            
            if (existingVar == null && existingArray == null) {
                String dataType = inferDataType(value);
                
                if ("array".equals(dataType)) {
                    // Arrays can ONLY be defined using list comprehension form:
                    // [[0 for _ in range(DIM_SIZE)] ...] or [0 for _ in range(DIM_SIZE)]
                    // and must be initialized with 0
                    if (!(value instanceof ListCompNode)) {
                        throw new CompilationException(null, null, 
                            "Arrays can only be defined using list comprehension form: [0 for _ in range(size)] or nested comprehensions");
                    }
                    
                    Array array = new Array();
                    String uuid = UUID.randomUUID().toString();
                    String scopedId = getScopedId(variableScope);
                    array.setId(!scopedId.isEmpty() ? scopedId + uuid : uuid + "_name_" + varName);
                    array.setName(varName);
                    array.setDataType("array");
                    
                    boolean hasNonConstantDimension = false;
                    List<Integer> constantDims = new ArrayList<>();
                    List<String> resolvedDims = new ArrayList<>();
                    
                    boolean[] hasNonConstant = new boolean[]{false};
                    resolvedDims = extractArrayDimensionsFromListComp((ListCompNode) value, variableScope, constantDims, hasNonConstant);
                    hasNonConstantDimension = hasNonConstant[0];
                    
                    // Only set dimension if all dimensions are constant
                    if (!hasNonConstantDimension) {
                        array.setDimension(constantDims);
                    }
                    
                    ruleEngineInput.getArrays().add(array);
                    codeConverter.setArray(array, getScope(variableScope));
                    inferredTypes.put(varName, "array");
                    
                    // If any dimension is not constant, emit RedefineArrayCommand
                    if (hasNonConstantDimension && !resolvedDims.isEmpty()) {
                        in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand redefineCmd = 
                            new in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand();
                        redefineCmd.setId(UUID.randomUUID().toString());
                        redefineCmd.setArrayId(array.getId());
                        redefineCmd.setNewDimensions(resolvedDims);
                        command.setRedefineArrayCommand(redefineCmd);
                    }
                    
                    // Track array in function frame if inside a function
                    if (variableFrameMap != null && counter != null) {
                        array.setFrameCount(counter[0]);
                        variableFrameMap.put(counter[0]++, array);
                    }
                } else {
                    Variable variable = new Variable();
                    variable.setId(getScopedId(variableScope) + UUID.randomUUID().toString());
                    variable.setName(varName);
                    variable.setDataType(dataType);
                    
                    ruleEngineInput.getVariables().add(variable);
                    codeConverter.setVariable(variable, getScope(variableScope));
                    inferredTypes.put(varName, dataType);
                    
                    // Track variable in function frame if inside a function
                    if (variableFrameMap != null && counter != null) {
                        variable.setFrameCount(counter[0]);
                        variableFrameMap.put(counter[0]++, variable);
                    }
                    
                    // Check if value is a function call
                    if (value instanceof CallNode) {
                        FunctionCall functionCall = convertFunctionCallWithReturnTargets(
                            (CallNode) value, variableScope, Arrays.asList(variable.getId()));
                        command.setFunctionCall(functionCall);
                    } else {
                        Operation operation = createAssignmentOperation(variable.getId(), value, variableScope);
                        command.setOperation(operation.getId());
                    }
                }
            } else {
                String targetId = existingVar != null ? existingVar.getId() : existingArray.getId();
                
                // Check if value is a function call - handle both variables and arrays
                if (value instanceof CallNode) {
                    FunctionCall functionCall = convertFunctionCallWithReturnTargets(
                        (CallNode) value, variableScope, Arrays.asList(targetId));
                    command.setFunctionCall(functionCall);
                } else {
                    Operation operation = createAssignmentOperation(targetId, value, variableScope);
                    command.setOperation(operation.getId());
                }
            }
            
            debugLevelCodeCreator.concat(varName + " = ");
            appendValueToDebug(value);
            debugLevelCodeCreator.nextLine();
            
        } else if (target instanceof SubscriptNode) {
            convertArrayAssignment((SubscriptNode) target, value, command, variableScope);
        } else if (target instanceof TupleNode) {
            convertTupleUnpackingAssignment((TupleNode) target, value, command, variableScope, variableFrameMap, counter);
        }
    }
    
    /**
     * Converts an augmented assignment statement into an Operation.
     * 
     * <p>Augmented assignments (+=, -=, *=, etc.) are converted by creating a synthetic
     * BinOpNode that represents the equivalent binary operation, then creating an
     * assignment operation.</p>
     * 
     * <h4>Conversion Logic</h4>
     * <pre>
     * Python: x += 5
     * AST: AugAssignNode(target=NameNode('x'), op='Add', value=ConstantNode(5))
     * 
     * Equivalent to: x = x + 5
     * 
     * Creates:
     *   - Constant {id: "const_5_id", value: "5"}
     *   - Operation (addition) {id: "add_op_id", operatorType: "+",
     *                          operand1: "x_var_id", operand2: "const_5_id"}
     *   - Operation (assignment) {id: "assign_op_id", operatorType: "=",
     *                            operand1: "x_var_id", operand2: "add_op_id"}
     *   - Command.operationId = "assign_op_id"
     * </pre>
     * 
     * <h4>Supported Operators</h4>
     * <ul>
     *   <li><b>Add</b> (+) - Addition assignment: x += 5</li>
     *   <li><b>Sub</b> (-) - Subtraction assignment: x -= 3</li>
     *   <li><b>Mult</b> (*) - Multiplication assignment: x *= 2</li>
     *   <li><b>Div</b> (/) - Division assignment: x /= 4</li>
     *   <li><b>Mod</b> (%) - Modulo assignment: x %= 10</li>
     *   <li><b>Pow</b> (^) - Power assignment: x **= 2</li>
     * </ul>
     * 
     * @param augAssign The AugAssignNode to convert
     * @param command The Command object to populate
     * @param variableScope Current scope stack for variable resolution
     * @throws CompilationException If variable doesn't exist or conversion fails
     */
    private void convertAugAssign(AugAssignNode augAssign, Command command, List<String> variableScope) 
            throws CompilationException {
        
        AstNode target = augAssign.getTarget();
        String op = augAssign.getOp();
        AstNode value = augAssign.getValue();
        
        if (target instanceof NameNode) {
            NameNode nameNode = (NameNode) target;
            String varName = nameNode.getId();
            
            Variable variable = getExistingVariable(varName, variableScope);
            Array array = getExistingArray(varName, variableScope);
            MethodDataTypeAgnosticArg methodArg = getExistingMethodArg(varName, variableScope);
            
            String targetId;
            
            // If it's a method arg, convert it to Variable first
            if (methodArg != null) {
                ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(methodArg);
                Variable newVar = new Variable();
                newVar.setId(methodArg.getId());
                newVar.setName(methodArg.getName());
                // Data type will be determined from the operation result
                newVar.setDataType("Integer"); // Default, will be refined during operation
                
                ruleEngineInput.getVariables().add(newVar);
                String scope = getScope(variableScope);
                codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + varName);
                codeConverter.setVariable(newVar, scope);
                
                targetId = newVar.getId();
            } else if (variable != null) {
                targetId = variable.getId();
            } else if (array != null) {
                targetId = array.getId();
            } else {
                // Global fallback: try global scope when inside a function
                Variable globalVar = codeConverter.getVariableMap().get(varName);
                if (globalVar != null) {
                    targetId = globalVar.getId();
                } else {
                    Array globalArr = codeConverter.getArrayMap().get(varName);
                    if (globalArr != null) {
                        targetId = globalArr.getId();
                    } else {
                        throw new CompilationException(null, null, 
                            "Variable " + varName + " not found for augmented assignment");
                    }
                }
            }
            
            BinOpNode binOp = new BinOpNode();
            binOp.setLeft(target);
            binOp.setOp(op);
            binOp.setRight(value);
            
            Operation operation = createAssignmentOperation(targetId, binOp, variableScope);
            command.setOperation(operation.getId());
            
            debugLevelCodeCreator.concat(varName + " " + opToPython(op) + "= ");
            appendValueToDebug(value);
            debugLevelCodeCreator.nextLine();
        }
    }
    
    /**
     * Converts an array element assignment statement.
     * 
     * <p>Handles assignments to array elements using subscript notation. Currently supports
     * simple array indexing with variable or constant indices. Complex expressions as
     * array names are not yet supported.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python: arr[0] = 10
     * AST: SubscriptNode(
     *        value=NameNode('arr'),
     *        slice=ConstantNode(0),
     *        ctx='Store')
     * 
     * Debug Output: "arr[0] = 10"
     * </pre>
     * 
     * <h4>Supported Patterns</h4>
     * <ul>
     *   <li><b>Constant index:</b> arr[0] = value, arr[5] = data</li>
     *   <li><b>Variable index:</b> arr[i] = value, arr[index] = data</li>
     *   <li><b>Expression value:</b> arr[0] = x + 5, arr[i] = compute()</li>
     * </ul>
     * 
     * <h4>Limitations</h4>
     * <ul>
     *   <li>Only simple array names supported (not expressions like func()[0] = x)</li>
     *   <li>Single-dimensional indexing only (arr[i], not arr[i][j])</li>
     * </ul>
     * 
     * @param target The SubscriptNode representing the array element being assigned
     * @param value The value expression being assigned to the array element
     * @param command The Command object to populate
     * @param variableScope Current scope stack for array resolution
     * @throws CompilationException If array doesn't exist or indexing is too complex
     */
    private void convertArrayAssignment(SubscriptNode target, AstNode value, Command command, 
                                       List<String> variableScope) throws CompilationException {
        
        // Extract array name and all indices (handles nested subscripts like arr[x1][y1])
        SubscriptExtractionResult extractionResult = extractArrayNameAndIndices(target, variableScope);
        String arrayVarName = extractionResult.arrayName;
        List<String> indices = extractionResult.indices;

        Array array = null;
        MethodDataTypeAgnosticArg methodArg = getExistingMethodArg(arrayVarName, variableScope);
        if (methodArg != null) {
            array = new Array();
            array.setId(methodArg.getId());
            array.setName(methodArg.getName());
            array.setDataType("array");
            ruleEngineInput.getArrays().add(array);
            String scope = getScope(variableScope);
            codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + arrayVarName);
            codeConverter.setArray(array, scope);
            ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(methodArg);


        } else {
            array = getExistingArray(arrayVarName, variableScope);
            // Global fallback: try global scope when inside a function
            if (array == null) {
                array = codeConverter.getArrayMap().get(arrayVarName);
            }
        }
        if (array == null) {
            throw new CompilationException(null, null, "Array " + arrayVarName + " not found");
        }
        
        // Create assignment operation (which creates ArrayCommand with indices internally)
        Operation operation = createAssignmentOperation(array.getId(), value, variableScope, indices);
        command.setOperation(operation.getId());
        
        debugLevelCodeCreator.concat(arrayVarName + "[");
        appendValueToDebug(target.getSlice());
        debugLevelCodeCreator.concat("] = ");
        appendValueToDebug(value);
        debugLevelCodeCreator.nextLine();
    }
    
    /**
     * Extracts array name and all indices from nested subscripts.
     * 
     * <p>Handles both simple indexing (arr[i]) and nested indexing (arr[x1][y1])
     * by recursively unwrapping SubscriptNodes.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Input: arr[x1][y1]
     * 
     * AST Structure:
     *   Subscript(
     *     value=Subscript(value=Name('arr'), slice=x1),
     *     slice=y1)
     * 
     * Returns:
     *   arrayName: "arr"
     *   indices: [x1_commandId, y1_commandId]
     * </pre>
     * 
     * @param target The SubscriptNode to extract from
     * @param variableScope Current scope stack for variable resolution
     * @return SubscriptExtractionResult containing array name and list of indices
     * @throws CompilationException If the base is not a simple array name
     */
    private SubscriptExtractionResult extractArrayNameAndIndices(SubscriptNode target, 
                                                                 List<String> variableScope) throws CompilationException {
        List<String> indices = new ArrayList<>();
        AstNode current = target;
        
        // Recursively unwrap nested subscripts
        while (current instanceof SubscriptNode) {
            SubscriptNode subscript = (SubscriptNode) current;
            
            // TODO: interpreter doesn't yet support operations as array indices
            AstNode sliceNode = subscript.getSlice();
            if (sliceNode instanceof BinOpNode || sliceNode instanceof UnaryOpNode) {
                throw new CompilationException(null, null, "Array index expressions are not yet supported");
            }

            // Convert the slice to the direct entity ID (variable/constant), not a command ID
            String indexId = getArgumentId(sliceNode, variableScope, false);
            indices.add(0, indexId); // Add at front to maintain left-to-right order
            
            current = subscript.getValue();
        }
        
        // Verify base is a simple name node
        if (!(current instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex array indexing: base must be a simple array name");
        }
        
        NameNode arrayName = (NameNode) current;
        
        return new SubscriptExtractionResult(arrayName.getId(), indices);
    }
    
    /**
     * Helper class to return array name and indices from subscript extraction.
     */
    private static class SubscriptExtractionResult {
        String arrayName;
        List<String> indices;
        
        SubscriptExtractionResult(String arrayName, List<String> indices) {
            this.arrayName = arrayName;
            this.indices = indices;
        }
    }
    
    /**
     * Converts tuple unpacking assignment from function call results.
     * 
     * <p>Handles assignments like: a, b = func()</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python:
     *   def get_coords():
     *       return 10, 20
     *   
     *   a, b = get_coords()
     * 
     * AST:
     *   AssignNode(
     *     targets=[TupleNode(elts=[Name('a'), Name('b')])],
     *     value=CallNode(func=Name('get_coords'), args=[]))
     * 
     * Implementation:
     *   1. Create variables for each tuple element (a, b)
     *   2. Pass these variables as additional arguments to the function
     *   3. Function will assign return values to these variables
     * </pre>
     * 
     * <h4>TODO</h4>
     * <ul>
     *   <li>Arrays cannot be returned in tuple unpacking (only scalar values supported)</li>
     * </ul>
     * 
     * @param target The TupleNode containing target variable names
     * @param value The value expression (expected to be CallNode)
     * @param command The Command object to populate
     * @param variableScope Current scope stack for variable resolution
     * @param variableFrameMap Map to track variables/arrays created in function body
     * @param counter Array containing current frame counter
     * @throws CompilationException If value is not a CallNode or conversion fails
     */
    private void convertTupleUnpackingAssignment(TupleNode target, AstNode value, Command command,
                                                  List<String> variableScope,
                                                  Map<Integer, RuleEngineInputUnits> variableFrameMap,
                                                  int[] counter) throws CompilationException {
        
        // Tuple unpacking currently only supported for function calls
        if (!(value instanceof CallNode)) {
            throw new CompilationException(null, null,
                "Tuple unpacking currently only supported for function call results: a, b = func()");
        }
        
        CallNode callNode = (CallNode) value;
        List<AstNode> tupleElements = target.getElts();
        
        if (tupleElements == null || tupleElements.isEmpty()) {
            throw new CompilationException(null, null,
                "Tuple unpacking requires at least one target variable");
        }
        
        // TODO: Arrays cannot be returned in tuple unpacking - only scalar values are supported
        
        // Create or get variables for each tuple element
        List<String> targetVariableIds = new ArrayList<>();
        StringBuilder debugStr = new StringBuilder();
        
        for (int i = 0; i < tupleElements.size(); i++) {
            AstNode element = tupleElements.get(i);
            
            if (!(element instanceof NameNode)) {
                throw new CompilationException(null, null,
                    "Tuple unpacking only supports simple variable names, not: " + element.getClass().getSimpleName());
            }
            
            NameNode nameNode = (NameNode) element;
            String varName = nameNode.getId();
            
            if (i > 0) {
                debugStr.append(", ");
            }
            debugStr.append(varName);
            
            // Check if variable already exists
            Variable existingVar = getExistingVariable(varName, variableScope);
            MethodDataTypeAgnosticArg existingMethodArg = getExistingMethodArg(varName, variableScope);
            
            if (existingMethodArg != null) {
                // Convert MethodArg to Variable (assume scalar return from function)
                ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(existingMethodArg);
                Variable variable = new Variable();
                variable.setId(existingMethodArg.getId());
                variable.setName(existingMethodArg.getName());
                variable.setDataType("Double"); // Default for return values
                
                ruleEngineInput.getVariables().add(variable);
                String scope = getScope(variableScope);
                codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + varName);
                codeConverter.setVariable(variable, scope);
                
                targetVariableIds.add(variable.getId());
            } else if (existingVar != null) {
                // Use existing variable
                targetVariableIds.add(existingVar.getId());
            } else {
                // Create new variable
                Variable variable = new Variable();
                variable.setId(getScopedId(variableScope) + UUID.randomUUID().toString());
                variable.setName(varName);
                variable.setDataType("Double"); // Default for return values
                
                ruleEngineInput.getVariables().add(variable);
                codeConverter.setVariable(variable, getScope(variableScope));
                inferredTypes.put(varName, "Double");
                
                // Track variable in function frame if inside a function
                if (variableFrameMap != null && counter != null) {
                    variable.setFrameCount(counter[0]);
                    variableFrameMap.put(counter[0]++, variable);
                }
                
                targetVariableIds.add(variable.getId());
            }
        }
        
        // Convert the function call with additional return target arguments
        FunctionCall functionCall = convertFunctionCallWithReturnTargets(callNode, variableScope, targetVariableIds);
        command.setFunctionCall(functionCall);
        
        debugLevelCodeCreator.concat(debugStr.toString() + " = ");
        appendValueToDebug(value);
        debugLevelCodeCreator.nextLine();
    }
    
    /**
     * Converts an if statement into an If block with Condition and command chains.
     * 
     * <p>Creates an If block containing the condition and references to the first commands
     * of both the if-body and else-body. The if block ID is added to variableScope to
     * properly scope any variables declared within the branches.</p>
     * 
     * <h4>Simple If Example</h4>
     * <pre>
     * Python:
     *   if x > 5:
     *       y = 10
     * 
     * AST:
     *   IfNode(
     *     test=CompareNode(left=Name('x'), ops=['Gt'], comparators=[Constant(5)]),
     *     body=[AssignNode(targets=[Name('y')], value=Constant(10))],
     *     orelse=[])
     * 
     * Creates:
     *   - Condition {id: "cond_id", conditionType: ">",
     *               comparisionCommand1: "x_var_id", comparisionCommand2: "const_5_id"}
     *   - Command (y=10) {id: "cmd_y_id", operationId: "op_y_id"}
     *   - If Block {id: "if_id", conditionId: "cond_id", ifCommand: "cmd_y_id", elseCommandId: null}
     *   - Command (if stmt) {id: "cmd_if_id", ifBlocks: "if_id"}
     * </pre>
     * 
     * <h4>If-Else Example</h4>
     * <pre>
     * Python:
     *   if x > 5:
     *       y = 10
     *   else:
     *       y = 20
     * 
     * Creates:
     *   - If Block {id: "if_id", conditionId: "cond_id",
     *              ifCommand: "cmd_y_10_id", elseCommandId: "cmd_y_20_id"}
     * </pre>
     * 
     * <h4>Scope Management</h4>
     * <pre>
     * Before: variableScope = []
     * During if body: variableScope = ["if_id_123"]
     *   Variables declared: "if_id_123var_name"
     * After: variableScope = []
     * </pre>
     * 
     * @param ifNode The IfNode to convert
     * @param command The Command object to populate with If block reference
     * @param variableScope Current scope stack (if block ID added/removed during conversion)
     * @param variableFrameMap Map to track variables/arrays created in function body (null for non-function contexts)
     * @param counter Array containing current frame counter (modified during conversion)
     * @throws CompilationException If condition or body conversion fails
     */
    private void convertIf(IfNode ifNode, Command command, List<String> variableScope,
                          Map<Integer, RuleEngineInputUnits> variableFrameMap,
                          int[] counter,
                          RuleEngineInputUnits parentScopeUnit) 
            throws CompilationException {
        
        If ifBlock = new If();
        ifBlock.setId("if_" + UUID.randomUUID().toString());
        
        // Set parent for the if block itself
        if (parentScopeUnit != null) {
            ifBlock.setImmediateParentRuleEngineInputUnitId(parentScopeUnit.getId());
        }
        
        String currentScope = variableScope.isEmpty() ? "" : variableScope.get(variableScope.size() - 1) + "_";
        variableScope.add(currentScope + ifBlock.getId());
        
        Condition condition = convertCondition(ifNode.getTest(), variableScope);
        ifBlock.setConditionId(condition.getId());
        
        debugLevelCodeCreator.concat("if ");
        appendValueToDebug(ifNode.getTest());
        debugLevelCodeCreator.concat(":");
        debugLevelCodeCreator.addIndentation();
        debugLevelCodeCreator.nextLine();
        
        List<Command> ifCommands = convertBody(ifNode.getBody(), variableScope, variableFrameMap, counter, ifBlock);
        if (!ifCommands.isEmpty()) {
            ifBlock.setIfCommand(ifCommands.get(0).getId());
        }
        
        debugLevelCodeCreator.decrementIndentation();
        
        if (!ifNode.getOrelse().isEmpty()) {
            debugLevelCodeCreator.concat("else:");
            debugLevelCodeCreator.addIndentation();
            debugLevelCodeCreator.nextLine();
            
            List<Command> elseCommands = convertBody(ifNode.getOrelse(), variableScope, variableFrameMap, counter, ifBlock);
            if (!elseCommands.isEmpty()) {
                ifBlock.setElseCommandId(elseCommands.get(0).getId());
            }
            
            debugLevelCodeCreator.decrementIndentation();
        }
        
        ruleEngineInput.getIfBlocks().add(ifBlock);
        command.setIfBlocks(ifBlock.getId());
        
        variableScope.remove(variableScope.size() - 1);
    }
    
    /**
     * Converts a while loop into a While block with Condition and loop command chain.
     * 
     * <p>Creates a While block containing the loop condition and a reference to the first
     * command in the loop body. The while block ID is added to variableScope to properly
     * scope any variables declared within the loop.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python:
     *   count = 5
     *   while count > 0:
     *       count -= 1
     * 
     * AST:
     *   WhileNode(
     *     test=CompareNode(left=Name('count'), ops=['Gt'], comparators=[Constant(0)]),
     *     body=[AugAssignNode(target=Name('count'), op='Sub', value=Constant(1))],
     *     orelse=[])
     * 
     * Creates:
     *   - Condition {id: "cond_id", conditionType: ">",
     *               comparisionCommand1: "count_var_id", comparisionCommand2: "const_0_id"}
     *   - Command (count -= 1) {id: "cmd_decr_id", operationId: "op_decr_id"}
     *   - While Block {id: "while_id", conditionId: "cond_id", loopCommand: "cmd_decr_id"}
     *   - Command (while stmt) {id: "cmd_while_id", whileBlock: "while_id"}
     * </pre>
     * 
     * <h4>Loop Body Command Chain</h4>
     * <pre>
     * Python:
     *   while condition:
     *       x = x + 1
     *       y = y * 2
     *       z = x + y
     * 
     * Loop commands linked:
     *   Command1 {id: "cmd1", nextId: "cmd2"} ← loopCommand points here
     *   Command2 {id: "cmd2", nextId: "cmd3"}
     *   Command3 {id: "cmd3", nextId: null}
     * </pre>
     * 
     * <h4>Scope Management</h4>
     * <pre>
     * Before: variableScope = []
     * During loop body: variableScope = ["while_id_456"]
     *   Variables declared: "while_id_456var_name"
     * After: variableScope = []
     * </pre>
     * 
     * @param whileNode The WhileNode to convert
     * @param command The Command object to populate with While block reference
     * @param variableScope Current scope stack (while block ID added/removed during conversion)
     * @param variableFrameMap Map to track variables/arrays created in function body (null for non-function contexts)
     * @param counter Array containing current frame counter (modified during conversion)
     * @throws CompilationException If condition or body conversion fails
     */
    private void convertWhile(WhileNode whileNode, Command command, List<String> variableScope,
                             Map<Integer, RuleEngineInputUnits> variableFrameMap,
                             int[] counter,
                             RuleEngineInputUnits parentScopeUnit) 
            throws CompilationException {
        
        While whileBlock = new While();
        whileBlock.setId("while_" + UUID.randomUUID().toString());
        
        // Set parent for the while block itself
        if (parentScopeUnit != null) {
            whileBlock.setImmediateParentRuleEngineInputUnitId(parentScopeUnit.getId());
        }
        
        String currentScope = variableScope.isEmpty() ? "" : variableScope.get(variableScope.size() - 1) + "_";
        variableScope.add(currentScope + whileBlock.getId());
        
        Condition condition = convertCondition(whileNode.getTest(), variableScope);
        whileBlock.setConditionId(condition.getId());
        
        debugLevelCodeCreator.concat("while ");
        appendValueToDebug(whileNode.getTest());
        debugLevelCodeCreator.concat(":");
        debugLevelCodeCreator.addIndentation();
        debugLevelCodeCreator.nextLine();
        
        List<Command> bodyCommands = convertBody(whileNode.getBody(), variableScope, variableFrameMap, counter, whileBlock);
        if (!bodyCommands.isEmpty()) {
            whileBlock.setWhileCommandId(bodyCommands.get(0).getId());
        }
        
        debugLevelCodeCreator.decrementIndentation();
        
        ruleEngineInput.getWhileBlocks().add(whileBlock);
        command.setWhileId(whileBlock.getId());
        
        variableScope.remove(variableScope.size() - 1);
    }
    
    /**
     * Converts a function definition statement (currently minimal support).
     * 
     * <p>Function definitions are currently only processed for debug output generation.
     * Full function support including local variables, return values, and function calls
     * is planned but not yet implemented.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python:
     *   def add(a, b):
     *       return a + b
     * 
     * AST:
     *   FunctionDefNode(
     *     name='add',
     *     args=arguments(args=[arg('a'), arg('b')]),
     *     body=[ReturnNode(...)])
     * 
     * Debug Output: "def add(a, b):"
     * </pre>
     * 
     * @param funcDef The FunctionDefNode to process
     * @param command The Command object (unused, function returns null from convertStatement)
     */
    private void convertFunctionDef(FunctionDefNode funcDef, Command command, List<String> variableScopeNotUsed, RuleEngineInputUnits parentScopeUnit) throws CompilationException {
        debugLevelCodeCreator.concat("def " + funcDef.getName() + "(");
        List<String> paramNames = new ArrayList<>();
        List<String> paramIds = new ArrayList<>();
        int[] counter = new int[]{0};
        Map<Integer, RuleEngineInputUnits> variableFrameMap = new HashMap<>();
        List<String> variableScope = new ArrayList<>();
        variableScope.add("");
        variableScope.add("func_" + funcDef.getName() + "_");
        
        // Track the current function being defined
        String previousFunctionName = this.currentFunctionName;
        this.currentFunctionName = funcDef.getName();
        
        for (ArgNode arg : funcDef.getArgs().getArgs()) {
            String argStr = arg.getArg();
            MethodDataTypeAgnosticArg methodArg = new MethodDataTypeAgnosticArg();
            methodArg.setName(argStr);
            methodArg.setFrameCount(counter[0]);
            methodArg.setId("arg_" + UUID.randomUUID().toString());
            paramNames.add(arg.getArg());
            paramIds.add(methodArg.getId());

            ruleEngineInput.getMethodDataTypeAgnosticArgs().add(methodArg);
            codeConverter.setMethodDataTypeAgnosticArgMap(methodArg, variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "");
            variableFrameMap.put(counter[0]++, methodArg);
        }
        debugLevelCodeCreator.concat(String.join(", ", paramNames));
        debugLevelCodeCreator.concat("):");
        debugLevelCodeCreator.nextLine();
        
        // Check if this function returns values and add return target parameters
        Integer returnCount = functionReturnCounts.get(funcDef.getName());
        if (returnCount != null && returnCount > 0) {
            // Add extra parameters for return targets
            for (int i = 0; i < returnCount; i++) {
                Variable returnTargetArg = new Variable();
                returnTargetArg.setName("_return_target_" + i);
                returnTargetArg.setFrameCount(counter[0]);
                returnTargetArg.setId("return_target_arg_" + UUID.randomUUID().toString());
                paramIds.add(returnTargetArg.getId());
                
                ruleEngineInput.getVariables().add(returnTargetArg);
                codeConverter.setVariable(returnTargetArg, variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "");
                variableFrameMap.put(counter[0]++, returnTargetArg);
            }
        }

        // Store the function arguments for use in convertReturn
        functionDefinitionArgs.put(funcDef.getName(), new ArrayList<>(paramIds));

        FunctionCall functionCall = new FunctionCall();
        functionCall.setId(funcDef.getName());
        
        // Set parent for the function call itself (usually null for top-level functions)
        if (parentScopeUnit != null) {
            functionCall.setImmediateParentRuleEngineInputUnitId(parentScopeUnit.getId());
        }
        
        List<Command> bodyCommands = convertBody(funcDef.getBody(), variableScope, variableFrameMap, counter, functionCall);
        if (!bodyCommands.isEmpty()) {
            functionCall.setFirstCommandId(bodyCommands.get(0).getId());
        }

        functionCall.setArguments(paramIds);
        functionCall.setId(funcDef.getName());

        List<String> variablesInFunction = new ArrayList<>();
        int frameCounter = 0;
        while(true) {
            RuleEngineInputUnits units = variableFrameMap.get(frameCounter);
            if(units == null) {
                break;
            }
            variablesInFunction.add(units.getId());
            frameCounter++;
        }

        functionCall.setAllVariablesInMethod(variablesInFunction);

        // ---- GPU function handling ----
        // Functions named with the "_GPU_N" suffix are executed as OpenCL kernels.
        // Convention: def funcName_GPU_N(dataArg1, ..., dataArgK, rangeDim1, ..., rangeDimN)
        //   - N (in the function name) = work_dim passed to clEnqueueNDRangeKernel.
        //   - Last N params  → range dim args → get_global_id(0..N-1) declarations in kernel.
        //   - First K params → __global float* data args in the kernel signature.
        if (funcDef.getName().matches(".*_GPU_\\d+$")) {
            try {
                // Build a map of all non-GPU helper functions visible to this GPU kernel.
                // GPU-suffixed functions are excluded because they cannot be called as device functions.
                Map<String, FunctionDefNode> helpers = new HashMap<>();
                for (Map.Entry<String, FunctionDefNode> entry : allModuleFunctions.entrySet()) {
                    String fname = entry.getKey();
                    if (!fname.equals(funcDef.getName()) && !fname.matches(".*_GPU_\\d+$")) {
                        helpers.put(fname, entry.getValue());
                    }
                }
                GpuFunctionBodyConverter gpuConverter = new GpuFunctionBodyConverter();
                GpuFunctionBodyConverter.GpuConversionResult gpuResult = gpuConverter.convert(funcDef, helpers);
                functionCall.setIsGpu(true);
                functionCall.setOpenClCode(gpuResult.kernelCode);
                functionCall.setGpuParallelismArgIndices(gpuResult.parallelismArgIndices);
                System.out.println("========== GPU FUNCTION DETECTED: " + funcDef.getName() + " ==========");
                System.out.println("OpenCL kernel code:\n" + gpuResult.kernelCode);
                System.out.println("GPU parallelism arg indices (work_dim=" + gpuResult.parallelismArgIndices.size() + "): " + gpuResult.parallelismArgIndices);
                System.out.println("=======================================================");
            } catch (Exception e) {
                throw new CompilationException(null, null,
                        "Failed to generate OpenCL kernel for GPU function '"
                        + funcDef.getName() + "': " + e.getMessage());
            }
        }

        ruleEngineInput.getFunctionCalls().add(functionCall);
        
        // Restore previous function name
        this.currentFunctionName = previousFunctionName;

    }
    
    /**
     * Converts a return statement into Command fields.
     * 
     * <p>Return statements in functions specify which values to return to the caller.
     * When a function is called with return targets (e.g., a, b = func()), the return
     * values are matched with the target variables at runtime to create assign operations.</p>
     * 
     * <h4>Single Return Example</h4>
     * <pre>
     * Python:
     *   def get_value():
     *       x = 10
     *       return x
     * 
     * Creates:
     *   - Command {returnValueIds: ["x_var_id"], isReturnStatement: true}
     * </pre>
     * 
     * <h4>Multiple Return (Tuple) Example</h4>
     * <pre>
     * Python:
     *   def get_coords():
     *       x = 10
     *       y = 20
     *       return x, y
     * 
     * Creates:
     *   - Command {returnValueIds: ["x_var_id", "y_var_id"], isReturnStatement: true}
     * </pre>
     * 
     * <h4>Return Mechanism with Targets</h4>
     * <pre>
     * When called as: a, b = get_coords()
     * 
     * The returnValueIds [x_var_id, y_var_id] are matched with return targets [a_id, b_id]
     * At runtime, assign operations are created:
     *   - a = x  (Operation with operand1=a_id, operand2=x_id)
     *   - b = y  (Operation with operand1=b_id, operand2=y_id)
     * </pre>
     * 
     * @param returnNode The ReturnNode to convert
     * @param variableScope Current scope stack for variable resolution
     * @param parentScopeUnit The parent scope unit (typically a FunctionCall) for this return statement
     * @throws CompilationException If return value cannot be converted
     */
    private Command convertReturn(ReturnNode returnNode, List<String> variableScope, 
                                 RuleEngineInputUnits parentScopeUnit) 
            throws CompilationException {
        
        AstNode returnValue = returnNode.getValue();

        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        command.setCodeStrPtr(debugLevelCodeCreator.getLine());
        
        // Set parent for the return command
        if (parentScopeUnit != null) {
            command.setImmediateParentRuleEngineInputUnitId(parentScopeUnit.getId());
        }
        
        ruleEngineInput.getCommands().add(command);
        
        // Handle empty return (returns None)
        if (returnValue == null) {
            debugLevelCodeCreator.concat("return");
            debugLevelCodeCreator.nextLine();
            command.setReturnStatement(true);
            return command;
        }
        
        List<String> returnValueIds = new ArrayList<>();
        
        // Check if returning multiple values (tuple)
        if (returnValue instanceof TupleNode) {
            TupleNode tuple = (TupleNode) returnValue;
            List<AstNode> elements = tuple.getElts();
            
            if (elements != null) {
                for (AstNode element : elements) {
                    String valueId = getArgumentId(element, variableScope, false);
                    returnValueIds.add(valueId);
                }
            }
        } else {
            // Single return value
            String valueId = getArgumentId(returnValue, variableScope, true);
            returnValueIds.add(valueId);
        }
        
        // Store the return value IDs on the command
        command.setReturnValueIds(returnValueIds);
        
        // Retrieve return target IDs from function arguments if this function returns values
        List<String> returnTargetIds = null;
        Integer returnCount = functionReturnCounts.get(currentFunctionName);
        if (returnCount != null && returnCount > 0) {
            List<String> functionArgs = functionDefinitionArgs.get(currentFunctionName);
            if (functionArgs != null && functionArgs.size() >= returnCount) {
                // The last returnCount arguments are the return targets
                returnTargetIds = functionArgs.subList(functionArgs.size() - returnCount, functionArgs.size());
            }
        }
        
        // Create assignment pairs for return values if we have return targets
        List<ReturnAssignmentPair> returnAssignmentPairs = new ArrayList<>();
        
        if (returnTargetIds != null && !returnTargetIds.isEmpty()) {
            // Create assignment pairs for each return value: returnTargetIds[i] = returnValueIds[i]
            for (int i = 0; i < returnValueIds.size(); i++) {
                // operand2 is the return value - create a command wrapping it
                String returnValueId = returnValueIds.get(i);
                Command returnValueCommand = createCommandForVariableOrArrayForReturn(returnValueId);
                
                // operand1 is the return target variable from function arguments
                String returnTargetId = returnTargetIds.get(i);
                Command targetCommand = new Command();
                targetCommand.setId("command_" + UUID.randomUUID().toString());
                targetCommand.setCodeStrPtr(debugLevelCodeCreator.getLine());
                targetCommand.setVariableId(returnTargetId);
                
                // Set parent for the target command
                if (parentScopeUnit != null) {
                    targetCommand.setImmediateParentRuleEngineInputUnitId(parentScopeUnit.getId());
                }
                
                ruleEngineInput.getCommands().add(targetCommand);
                
                // Create the pair (target, source)
                ReturnAssignmentPair pair = new ReturnAssignmentPair();
                pair.setTargetCommandId(targetCommand.getId());
                pair.setSourceCommandId(returnValueCommand.getId());
                returnAssignmentPairs.add(pair);
            }
        }
        
        // Create the final command that marks the return statement
        Command returnCommand = new Command();
        returnCommand.setId("command_" + UUID.randomUUID().toString());
        returnCommand.setCodeStrPtr(debugLevelCodeCreator.getLine());
        returnCommand.setReturnStatement(true);
        returnCommand.setReturnValueIds(returnValueIds);
        returnCommand.setReturnAssignmentPairs(returnAssignmentPairs);
        
        // Set parent for the return command
        if (parentScopeUnit != null) {
            returnCommand.setImmediateParentRuleEngineInputUnitId(parentScopeUnit.getId());
        }
        
        ruleEngineInput.getCommands().add(returnCommand);
        
        debugLevelCodeCreator.concat("return ");
        appendValueToDebug(returnValue);
        debugLevelCodeCreator.nextLine();

        return returnCommand;
    }
    
    /**
     * Converts an expression statement (function/method calls executed for side effects).
     * 
     * <p>Expression statements are expressions that appear as standalone statements rather
     * than as part of assignments or conditions. Most commonly these are function calls
     * like print() or method calls like list.append().</p>
     * 
     * <h4>Function Call Example</h4>
     * <pre>
     * Python: print("Hello")
     * AST: ExprNode(value=CallNode(func=Name('print'), args=[Constant('Hello')]))
     * 
     * Converts to: FunctionCall structure
     * Debug Output: "print(\"Hello\")"
     * </pre>
     * 
     * <h4>List Append Example</h4>
     * <pre>
     * Python: my_list.append(5)
     * AST: ExprNode(
     *        value=CallNode(
     *          func=AttributeNode(value=Name('my_list'), attr='append'),
     *          args=[Constant(5)]))
     * 
     * Special handling: convertListAppend()
     * Debug Output: "my_list.append(5)"
     * </pre>
     * 
     * @param expr The ExprNode to convert
     * @param command The Command object to populate
     * @param variableScope Current scope stack for variable resolution
     * @throws CompilationException If expression cannot be converted
     */
    private void convertExpr(ExprNode expr, Command command, List<String> variableScope)
            throws CompilationException {

        AstNode value = expr.getValue();

        if (value instanceof CallNode) {
            CallNode call = (CallNode) value;
            if (call.getFunc() instanceof AttributeNode) {
                convertMethodCall(call, command, variableScope);
            } else {
                convertFunctionCall(call, command, variableScope);
            }
        }

        debugLevelCodeCreator.nextLine();
    }
    
    /**
     * Converts a list.append() method call (special handling).
     * 
     * <p>List append operations are recognized as a special case of method calls and
     * handled separately. Currently only generates debug output; full array modification
     * operations are planned for future implementation.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python: my_list.append(10)
     * AST: CallNode(
     *        func=AttributeNode(value=Name('my_list'), attr='append'),
     *        args=[Constant(10)])
     * 
     * Debug Output: "my_list.append(10)"
     * </pre>
     * 
     * @param listName The NameNode identifying the list variable
     * @param call The CallNode containing the append call details
     * @param command The Command object (currently unused)
     * @param variableScope Current scope stack (currently unused)
     */
    private void convertListAppend(NameNode listName, CallNode call, Command command, 
                                   List<String> variableScope) {
        
        String arrayVarName = listName.getId();
        
        debugLevelCodeCreator.concat(arrayVarName + ".append(");
        if (!call.getArgs().isEmpty()) {
            appendValueToDebug(call.getArgs().get(0));
        }
        debugLevelCodeCreator.concat(")");
    }
    
    /**
     * Converts a function call for debug output.
     * 
     * <p>Generates the debug representation of a function call with its arguments.
     * Currently only supports simple function calls (not method chains or complex expressions).</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python: calculate(x, 5, y + 2)
     * AST: CallNode(
     *        func=Name('calculate'),
     *        args=[Name('x'), Constant(5), BinOp(...)])
     * 
     * Debug Output: "calculate(x, 5, y + 2)"
     * </pre>
     * 
     * @param call The CallNode representing the function call
     * @param command The Command object (currently unused)
     * @param variableScope Current scope stack for argument resolution
     * @throws CompilationException If function reference is complex (not a simple name)
     */
    private void convertFunctionCall(CallNode call, Command command, List<String> variableScope) 
            throws CompilationException {
        
        if (!(call.getFunc() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex function calls not yet supported");
        }
        
        NameNode funcName = (NameNode) call.getFunc();
        String functionName = funcName.getId();
        
        FunctionCall functionCall = new FunctionCall();
        List<String> argumentIds = new ArrayList<>();
        
        // Build debug output: exec functionName(arg1, arg2, ...)
        debugLevelCodeCreator.concat("exec " + functionName + "(");
        
        boolean first = true;
        for (AstNode arg : call.getArgs()) {
            if (!first) {
                debugLevelCodeCreator.concat(", ");
            }
            
            // Get the ID of the argument (variable, array, or methodArg)
            String argumentId = getArgumentId(arg, variableScope, false);
            argumentIds.add(argumentId);
            
            // Add to debug output
            appendValueToDebug(arg);
            first = false;
        }
        
        debugLevelCodeCreator.concat(")");
        
        functionCall.setId(functionName);
        functionCall.setArguments(argumentIds);
        
        command.setFunctionCall(functionCall);
    }
    
    /**
     * Converts a function call with return target variables for tuple unpacking.
     * 
     * <p>Similar to convertFunctionCall but adds return target variable IDs as additional
     * arguments so the function can assign its return values directly to these variables.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python: a, b = get_coords(x, y)
     * 
     * Creates FunctionCall with:
     *   - Regular arguments: [x_id, y_id]
     *   - Return targets: [a_id, b_id]
     *   - Combined arguments: [x_id, y_id, a_id, b_id]
     * </pre>
     * 
     * @param call The CallNode representing the function call
     * @param variableScope Current scope stack for argument resolution
     * @param returnTargetIds List of variable IDs that will receive return values
     * @return The created FunctionCall object with all arguments
     * @throws CompilationException If function reference is complex
     */
    private FunctionCall convertFunctionCallWithReturnTargets(CallNode call, List<String> variableScope,
                                                               List<String> returnTargetIds) 
            throws CompilationException {
        
        if (!(call.getFunc() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex function calls not yet supported");
        }
        
        NameNode funcName = (NameNode) call.getFunc();
        String functionName = funcName.getId();
        
        FunctionCall functionCall = new FunctionCall();
        functionCall.setId(functionName);
        
        List<String> argumentIds = new ArrayList<>();
        
        // Add regular arguments first
        for (AstNode arg : call.getArgs()) {
            String argumentId = getArgumentId(arg, variableScope, false);
            argumentIds.add(argumentId);
        }
        
        // Add return target variables as additional arguments.
        // The function will receive these as extra parameters to assign return values to.
        argumentIds.addAll(returnTargetIds);
        
        functionCall.setArguments(argumentIds);
        
        // Record the expected return count for this function (for case where function is defined later)
        Integer existingCount = functionReturnCounts.get(functionName);
        if (existingCount == null || existingCount < returnTargetIds.size()) {
            functionReturnCounts.put(functionName, returnTargetIds.size());
            
            // If function is already defined, update its parameters to include return targets
            updateFunctionDefinitionWithReturnTargets(functionName, returnTargetIds.size());
        }
        
        //ruleEngineInput.getFunctionCalls().add(functionCall);
        
        return functionCall;
    }
    
    /**
     * Updates an existing function definition to include return target parameters.
     * 
     * <p>This is called when a function is called with tuple unpacking before it's defined,
     * or when we need to retroactively add return target parameters to an already-defined function.</p>
     * 
     * @param functionName The name of the function to update
     * @param returnCount The number of return values (and thus return target parameters to add)
     */
    private void updateFunctionDefinitionWithReturnTargets(String functionName, int returnCount) {
        // Find the existing FunctionCall definition
        FunctionCall existingDef = null;
        for (FunctionCall fc : ruleEngineInput.getFunctionCalls()) {
            if (fc.getId().equals(functionName)) {
                existingDef = fc;
                break;
            }
        }
        
        if (existingDef == null) {
            // Function not yet defined, will be handled when convertFunctionDef is called
            return;
        }
        
        // Check if return target parameters are already added
        List<String> currentArgs = existingDef.getArguments();
        if (currentArgs == null) {
            currentArgs = new ArrayList<>();
            existingDef.setArguments(currentArgs);
        }
        
        // Count how many return target args already exist (they have names starting with _return_target_)
        int existingReturnTargetCount = 0;
        for (String argId : currentArgs) {
            MethodDataTypeAgnosticArg methodArg = findMethodArgById(argId);
            if (methodArg != null && methodArg.getName().startsWith("_return_target_")) {
                existingReturnTargetCount++;
            }
        }
        
        // Add missing return target parameters
        if (existingReturnTargetCount < returnCount) {
            List<String> newArgIds = new ArrayList<>(currentArgs);
            int frameCount = currentArgs.size();

            List<String> allVariablesInFunction = existingDef.getAllVariablesInMethod();
            //New list in which we will have all the original arguments, then the return targets, and then the rest of variables from allVariablesInFunction
            List<String> updatedVariablesInFunction = new ArrayList<>();
            int startOfMethodVariables = currentArgs.size();

            
            for (int i = existingReturnTargetCount; i < returnCount; i++) {
                Variable returnTargetArg = new Variable();
                returnTargetArg.setName("_return_target_" + i);
                returnTargetArg.setFrameCount(frameCount++);
                returnTargetArg.setId("return_target_arg_" + UUID.randomUUID().toString());
                newArgIds.add(returnTargetArg.getId());
                
                ruleEngineInput.getVariables().add(returnTargetArg);
                codeConverter.setVariable(returnTargetArg, "func_" + functionName + "_");
            }
            
            existingDef.setArguments(newArgIds);
            updatedVariablesInFunction.addAll(newArgIds);
            //Add the rest of variables from allVariablesInFunction
            if (allVariablesInFunction != null && allVariablesInFunction.size() > startOfMethodVariables) {
                updatedVariablesInFunction.addAll(allVariablesInFunction.subList(startOfMethodVariables, allVariablesInFunction.size()));
            }
            existingDef.setAllVariablesInMethod(updatedVariablesInFunction);
            // Update the function arguments map for use in convertReturn
            functionDefinitionArgs.put(functionName, newArgIds);
            
            // Retroactively create assignments for return statements in this function
            createReturnAssignments(functionName, returnCount);
        }
    }
    
    /**
     * Creates return value assignments for a function that was already defined.
     * 
     * <p>When return target parameters are retroactively added to a function definition,
     * we need to update any return statements that have already been processed to include
     * the assignment operations.</p>
     * 
     * @param functionName The name of the function
     * @param returnCount The number of return values to assign
     */
    private void createReturnAssignments(String functionName, int returnCount) {
        List<String> functionArgs = functionDefinitionArgs.get(functionName);
        if (functionArgs == null || functionArgs.size() < returnCount) {
            return;
        }
        
        // Get the return target parameter IDs (the last returnCount arguments)
        List<String> returnTargetIds = functionArgs.subList(functionArgs.size() - returnCount, functionArgs.size());
        
        // Find the function definition to get its command chain
        FunctionCall funcDef = null;
        for (FunctionCall fc : ruleEngineInput.getFunctionCalls()) {
            if (fc.getId().equals(functionName) && fc.getFirstCommandId() != null) {
                funcDef = fc;
                break;
            }
        }
        
        if (funcDef == null) {
            return;
        }
        
        // Walk through the function's command chain to find return statements
        Set<String> functionCommandIds = collectFunctionCommandIds(funcDef.getFirstCommandId());
        
        // Find return statements that belong to this function and create assignments
        List<Command> commandList = new ArrayList<>(ruleEngineInput.getCommands());
        for (Command cmd : commandList) {
            if (cmd.getReturnStatement() != null && cmd.getReturnStatement() 
                    && cmd.getReturnValueIds() != null
                    && functionCommandIds.contains(cmd.getId())) {
                
                List<String> returnValueIds = cmd.getReturnValueIds();
                
                // Only create assignments if this is a return with values
                if (!returnValueIds.isEmpty() && returnValueIds.size() == returnCount) {
                    
                    // Check if assignment pairs already exist for this return statement
                    if (cmd.getReturnAssignmentPairs() == null || cmd.getReturnAssignmentPairs().isEmpty()) {
                        // Create assignment pairs for each return value
                        List<ReturnAssignmentPair> returnAssignmentPairs = new ArrayList<>();
                        
                        for (int i = 0; i < returnValueIds.size(); i++) {
                            // operand2 is the return value - create a command wrapping it
                            String returnValueId = returnValueIds.get(i);
                            Command returnValueCommand = createCommandForVariableOrArrayForReturn(returnValueId);
                            
                            // operand1 is the return target variable from function arguments
                            String returnTargetId = returnTargetIds.get(i);
                            Command targetCommand = new Command();
                            targetCommand.setId("command_" + UUID.randomUUID().toString());
                            targetCommand.setCodeStrPtr(cmd.getCodeStrPtr());
                            targetCommand.setVariableId(returnTargetId);
                            ruleEngineInput.getCommands().add(targetCommand);
                            
                            // Create the pair (target, source)
                            ReturnAssignmentPair pair = new ReturnAssignmentPair();
                            pair.setTargetCommandId(targetCommand.getId());
                            pair.setSourceCommandId(returnValueCommand.getId());
                            returnAssignmentPairs.add(pair);
                        }
                        
                        // Set the assignment pairs on the return command
                        cmd.setReturnAssignmentPairs(returnAssignmentPairs);
                    }
                }
            }
        }
    }
    
    /**
     * Collects all command IDs that belong to a function's command chain.
     * Recursively follows nextId, if/else branches, and while loops.
     */
    private Set<String> collectFunctionCommandIds(String startCommandId) {
        Set<String> commandIds = new HashSet<>();
        collectCommandIdsRecursive(startCommandId, commandIds);
        return commandIds;
    }
    
    private void collectCommandIdsRecursive(String commandId, Set<String> visited) {
        if (commandId == null || visited.contains(commandId)) {
            return;
        }
        
        Command cmd = findCommandById(commandId);
        if (cmd == null) {
            return;
        }
        
        visited.add(commandId);
        
        // Follow nextId
        collectCommandIdsRecursive(cmd.getNextId(), visited);
        
        // Follow if/else branches
        if (cmd.getIfBlocks() != null) {
            If ifBlock = findIfBlockById(cmd.getIfBlocks());
            if (ifBlock != null) {
                collectCommandIdsRecursive(ifBlock.getIfCommand(), visited);
                collectCommandIdsRecursive(ifBlock.getElseCommandId(), visited);
            }
        }
        
        // Follow while loop body
        if (cmd.getWhileId() != null) {
            While whileBlock = findWhileBlockById(cmd.getWhileId());
            if (whileBlock != null) {
                collectCommandIdsRecursive(whileBlock.getWhileCommandId(), visited);
            }
        }
    }
    
    private Command findCommandById(String id) {
        for (Command cmd : ruleEngineInput.getCommands()) {
            if (cmd.getId().equals(id)) {
                return cmd;
            }
        }
        return null;
    }
    
    private If findIfBlockById(String id) {
        for (If ifBlock : ruleEngineInput.getIfBlocks()) {
            if (ifBlock.getId().equals(id)) {
                return ifBlock;
            }
        }
        return null;
    }
    
    private While findWhileBlockById(String id) {
        for (While whileBlock : ruleEngineInput.getWhileBlocks()) {
            if (whileBlock.getId().equals(id)) {
                return whileBlock;
            }
        }
        return null;
    }
    
    /**
     * Finds a MethodDataTypeAgnosticArg by its ID.
     */
    private MethodDataTypeAgnosticArg findMethodArgById(String id) {
        for (MethodDataTypeAgnosticArg arg : ruleEngineInput.getMethodDataTypeAgnosticArgs()) {
            if (arg.getId().equals(id)) {
                return arg;
            }
        }
        return null;
    }
    
    /**
     * Finds existing assign commands that match the given return values and return targets.
     * Returns a list of assign commands if they exist, null otherwise.
     */
    private List<Command> findReturnAssignCommands(List<String> returnValueIds, List<String> returnTargetIds) {
        if (returnValueIds.size() != returnTargetIds.size()) {
            return null;
        }
        
        List<Command> assignCommands = new ArrayList<>();
        
        // Look for assign operations that match: returnTargetIds[i] = returnValueIds[i]
        for (int i = 0; i < returnValueIds.size(); i++) {
            String returnValueId = returnValueIds.get(i);
            String returnTargetId = returnTargetIds.get(i);
            
            Command foundCommand = null;
            
            // Search for a command with an operation that assigns returnValueId to returnTargetId
            for (Command cmd : ruleEngineInput.getCommands()) {
                if (cmd.getOperation() != null) {
                    Operation op = findOperationById(cmd.getOperation());
                    if (op != null && "=".equals(op.getOperatorType())) {
                        // Check if this operation assigns to the return target
                        Command operand1Cmd = findCommandById(op.getOperand1());
                        if (operand1Cmd != null && returnTargetId.equals(operand1Cmd.getVariableId())) {
                            // Check if the value being assigned is the return value
                            Command operand2Cmd = findCommandById(op.getOperand2());
                            if (operand2Cmd != null && returnValueId.equals(operand2Cmd.getVariableId())) {
                                foundCommand = cmd;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (foundCommand == null) {
                // If any assign command is missing, return null (we'll create all of them)
                return null;
            }
            
            assignCommands.add(foundCommand);
        }
        
        return assignCommands;
    }
    
    /**
     * Finds an Operation by its ID.
     */
    private Operation findOperationById(String id) {
        for (Operation op : ruleEngineInput.getOperations()) {
            if (op.getId().equals(id)) {
                return op;
            }
        }
        return null;
    }
    
    /**
     * Converts a list of statement nodes into a linked chain of Commands.
     * 
     * <p>Used to convert the body of if blocks, while loops, and function definitions.
     * Each statement is converted to a Command, and commands are linked via nextId
     * to maintain execution order.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python Body (inside if block):
     *   x = 5
     *   y = 10
     *   z = x + y
     * 
     * Returns:
     *   [
     *     Command1 {id: "cmd1", operationId: "op1", nextId: "cmd2"},
     *     Command2 {id: "cmd2", operationId: "op2", nextId: "cmd3"},
     *     Command3 {id: "cmd3", operationId: "op3", nextId: null}
     *   ]
     * </pre>
     * 
     * @param body List of AST statement nodes to convert
     * @param variableScope Current scope stack (passed to each statement conversion)
     * @param variableFrameMap Map to track variables/arrays created in function body (null for non-function contexts)
     * @param counter Array containing current frame counter (modified during conversion)
     * @return List of Commands linked via nextId references
     * @throws CompilationException If any statement conversion fails
     */
    private List<Command> convertBody(List<AstNode> body, List<String> variableScope,
                                     Map<Integer, RuleEngineInputUnits> variableFrameMap, 
                                     int[] counter,
                                     RuleEngineInputUnits parentScopeUnit) 
            throws CompilationException {
        
        List<Command> commands = new ArrayList<>();
        Command previousCommand = null;
        
        for (AstNode node : body) {
            Command command = convertStatement(node, variableScope, variableFrameMap, counter, parentScopeUnit);
            if (command != null) {
                commands.add(command);
                if (previousCommand != null) {
                    previousCommand.setNextId(command.getId());
                }
                previousCommand = command;
            }
        }
        return commands;
    }
    
    /**
     * Converts a test expression into a Condition object.
     * 
     * <p>Conditions are used in if statements and while loops. Currently only supports
     * CompareNode (comparison operations). Future support planned for boolean operations
     * (and/or/not) and truthiness checks.</p>
     * 
     * <h4>Example</h4>
     * <pre>
     * Python: x > 5
     * AST: CompareNode(
     *        left=Name('x'),
     *        ops=['Gt'],
     *        comparators=[Constant(5)])
     * 
     * Creates:
     *   Condition {
     *     id: "cond_uuid",
     *     conditionType: ">",
     *     comparisionCommand1: "x_var_id",
     *     comparisionCommand2: "const_5_id"
     *   }
     * </pre>
     * 
     * <h4>Supported Comparisons</h4>
     * <ul>
     *   <li><b>Lt</b> (&lt;) - Less than</li>
     *   <li><b>LtE</b> (&lt;=) - Less than or equal</li>
     *   <li><b>Gt</b> (&gt;) - Greater than</li>
     *   <li><b>GtE</b> (&gt;=) - Greater than or equal</li>
     *   <li><b>Eq</b> (==) - Equal</li>
     *   <li><b>NotEq</b> (!=) - Not equal</li>
     * </ul>
     * 
     * @param test The condition expression node to convert
     * @param variableScope Current scope stack for variable resolution
     * @return The created Condition object
     * @throws CompilationException If condition type is unsupported
     */
    private Condition convertCondition(AstNode test, List<String> variableScope) 
            throws CompilationException {
        
        if (test instanceof CompareNode) {
            CompareNode compare = (CompareNode) test;
            
            Condition condition = new Condition();
            condition.setId(UUID.randomUUID().toString());
            
            String op = compare.getOps().get(0);
            condition.setConditionType(mapCompareOp(op));
            
            // Convert expressions and wrap in Commands
            String leftCommandId = convertExpressionToCommand(compare.getLeft(), variableScope);
            String rightCommandId = convertExpressionToCommand(compare.getComparators().get(0), variableScope);
            
            condition.setComparisionCommand1(leftCommandId);
            condition.setComparisionCommand2(rightCommandId);
            
            ruleEngineInput.getConditions().add(condition);
            return condition;
        }
        
        throw new CompilationException(null, null, "Unsupported condition type");
    }
    
    /**
     * Creates an Operation object for assignment.
     * 
     * <p>Assignment operations have operatorType "=" and connect a target (Variable/Array)
     * to a value expression. The value expression is recursively converted and its ID
     * is used as operand2.</p>
     * 
     * <h4>Simple Assignment Example</h4>
     * <pre>
     * Python: x = 5
     * 
     * Creates:
     *   Constant {id: "const_5_id", value: "5", dataType: "Integer"}
     *   Operation {
     *     id: "op_uuid",
     *     operatorType: "=",
     *     operand1: "x_var_id",      // target variable
     *     operand2: "const_5_id"     // converted value expression
     *   }
     * </pre>
     * 
     * <h4>Expression Assignment Example</h4>
     * <pre>
     * Python: result = x + 5
     * 
     * Creates:
     *   Operation (addition) {id: "add_op_id", operatorType: "+", ...}
     *   Operation (assignment) {
     *     id: "assign_op_id",
     *     operatorType: "=",
     *     operand1: "result_var_id",
     *     operand2: "add_op_id"      // references the addition operation
     *   }
     * </pre>
     * 
     * @param targetId The ID of the target Variable or Array
     * @param value The value expression node to assign
     * @param variableScope Current scope stack for expression conversion
     * @return The created Operation object
     * @throws CompilationException If value expression cannot be converted
     */
    private Operation createAssignmentOperation(String targetId, AstNode value,
                                               List<String> variableScope) throws CompilationException {
        return createAssignmentOperation(targetId, value, variableScope, null);
    }
    
    /**
     * Creates an Operation object for assignment with optional array indices.
     * 
     * @param targetId The ID of the target Variable or Array
     * @param value The value expression node to assign
     * @param variableScope Current scope stack for expression conversion
     * @param arrayIndices Optional list of indices for array assignments (e.g., arr[i][j])
     * @return The created Operation object
     * @throws CompilationException If value expression cannot be converted
     */
    private Operation createAssignmentOperation(String targetId, AstNode value,
                                               List<String> variableScope, List<String> arrayIndices) 
            throws CompilationException {
        
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType("=");
        
        // Create command for operand1 (target variable/array)
        // Pass indices if this is an array assignment
        Command operand1Command = createCommandForVariableOrArray(targetId, arrayIndices);
        operation.setOperand1(operand1Command.getId());
        
        // Create command for operand2 (value expression)
        String valueCommandId = convertExpressionToCommand(value, variableScope);
        operation.setOperand2(valueCommandId);
        
        ruleEngineInput.getOperations().add(operation);
        return operation;
    }
    
    /**
     * Creates a Command object that wraps a variable or array by its ID.
     * If array indices are provided, they are set on the ArrayCommand.
     */
    private Command createCommandForVariableOrArrayForReturn(String entityId) {
        for (Command cmd : ruleEngineInput.getCommands()) {
            if (entityId.equals(cmd.getId())) {
                return cmd;
            }
        }
        return createCommandForVariableOrArray(entityId, null);
    }
    
    /**
     * Creates a Command object that wraps a variable or array by its ID with optional indices.
     * 
     * @param entityId The ID of the variable or array
     * @param indices Optional list of array indices; if provided and entityId is an array,
     *                these indices are set on the ArrayCommand
     * @return The created Command
     */
    private Command createCommandForVariableOrArray(String entityId, List<String> indices) {
        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        
        // Check if it's a variable or array
        Variable variable = findVariableById(entityId);
        if (variable != null) {
            command.setVariableId(variable.getId());
        } else {
            Array array = findArrayById(entityId);
            if (array != null) {
                ArrayCommand arrayCommand = new ArrayCommand();
                arrayCommand.setArrayId(array.getId());
                // Set indices if provided
                if (indices != null && !indices.isEmpty()) {
                    arrayCommand.setIndex(indices);
                }
                command.setArrayCommand(arrayCommand);
            } else {
                Constant constant = findConstantById(entityId);
                if (constant != null) {
                    command.setConstant(constant.getId());
                } else {
                    // Could be a method arg - set as variableId
                    command.setVariableId(entityId);
                }
            }
        }
        
        ruleEngineInput.getCommands().add(command);
        return command;
    }

    private Constant findConstantById(String id) {
        for (Constant c : ruleEngineInput.getConstants()) {
            if (id.equals(c.getId())) {
                return c;
            }
        }
        return null;
    }

    /**
     * Finds a Variable by its ID.
     */
    private Variable findVariableById(String id) {
        for (Variable v : ruleEngineInput.getVariables()) {
            if (id.equals(v.getId())) {
                return v;
            }
        }
        return null;
    }
    
    /**
     * Finds an Array by its ID.
     */
    private Array findArrayById(String id) {
        for (Array a : ruleEngineInput.getArrays()) {
            if (id.equals(a.getId())) {
                return a;
            }
        }
        return null;
    }
    
    /**
     * Converts an expression node and wraps it in a Command, returning the Command ID.
     */
    private String convertExpressionToCommand(AstNode expr, List<String> variableScope) 
            throws CompilationException {
        
        if (expr instanceof ConstantNode) {
            return convertConstantToCommand((ConstantNode) expr);
        } else if (expr instanceof NameNode) {
            return convertNameToCommand((NameNode) expr, variableScope);
        } else if (expr instanceof BinOpNode) {
            return convertBinOpToCommand((BinOpNode) expr, variableScope);
        } else if (expr instanceof UnaryOpNode) {
            return convertUnaryOpToCommand((UnaryOpNode) expr, variableScope).getId();
        } else if (expr instanceof SubscriptNode) {
            return convertSubscriptToCommand((SubscriptNode) expr, variableScope);
        } else if (expr instanceof CallNode) {
            return convertCallExpressionToCommand((CallNode) expr, variableScope);
        } else if (expr instanceof ListNode) {
            return convertListToCommand((ListNode) expr, variableScope);
        }
        
        throw new CompilationException(
            expr.getLineno() > 0 ? expr.getLineno() : null,
            expr.getColOffset(),
            "Unsupported expression type: " + expr.getClass().getSimpleName() + "\nAST: " + expr.toString());
    }
    
    /**
     * Converts a constant and wraps it in a Command.
     */
    private String convertConstantToCommand(ConstantNode constant) {
        Constant c = new Constant();
        c.setId(UUID.randomUUID().toString());
        
        Object value = constant.getValue();
        // Set the actual value (numeric or string), not a string conversion
        c.setValue(value);
        if (value instanceof Integer) {
            c.setDataType("Integer");
        } else if (value instanceof Double) {
            c.setDataType("Double");
        } else {
            c.setDataType("String");
        }
        
        ruleEngineInput.getConstants().add(c);
        
        // Wrap in command
        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        command.setConstant(c.getId());
        ruleEngineInput.getCommands().add(command);
        
        return command.getId();
    }
    
    /**
     * Converts a variable name reference and wraps it in a Command.
     */
    private String convertNameToCommand(NameNode name, List<String> variableScope) throws CompilationException {
        String varName = name.getId();
        
        Variable variable = getExistingVariable(varName, variableScope);
        if (variable != null) {
            Command command = new Command();
            command.setId("command_" + UUID.randomUUID().toString());
            command.setVariableId(variable.getId());
            ruleEngineInput.getCommands().add(command);
            return command.getId();
        }
        
        Array array = getExistingArray(varName, variableScope);
        if (array != null) {
            throw new CompilationException(null, null, 
                "Bare array reference '" + varName + "' without subscripting is not supported. " +
                "Array indices must be explicitly specified (e.g., arr[0], arr[i][j]). " +
                "If you need to pass the entire array, use array subscript notation.");
        }
        
        MethodDataTypeAgnosticArg methodArg = getExistingMethodArg(varName, variableScope);
        if (methodArg != null) {
            // Convert MethodArg to Variable when first used
            ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(methodArg);
            Variable newVar = new Variable();
            newVar.setId(methodArg.getId());
            newVar.setName(methodArg.getName());
            newVar.setDataType("Integer"); // Default type, will be refined during operations
            
            ruleEngineInput.getVariables().add(newVar);
            String scope = getScope(variableScope);
            codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + varName);
            codeConverter.setVariable(newVar, scope);
            
            Command command = new Command();
            command.setId("command_" + UUID.randomUUID().toString());
            command.setVariableId(newVar.getId());
            ruleEngineInput.getCommands().add(command);
            return command.getId();
        }
        
        // Global fallback: try global scope when inside a function
        if (isInsideFunction(variableScope)) {
            Variable globalVar = codeConverter.getVariableMap().get(varName);
            if (globalVar != null) {
                Command command = new Command();
                command.setId("command_" + UUID.randomUUID().toString());
                command.setVariableId(globalVar.getId());
                ruleEngineInput.getCommands().add(command);
                return command.getId();
            }
            Array globalArr = codeConverter.getArrayMap().get(varName);
            if (globalArr != null) {
                throw new CompilationException(null, null, 
                    "Bare array reference '" + varName + "' without subscripting is not supported. " +
                    "Array indices must be explicitly specified (e.g., arr[0], arr[i][j]). " +
                    "If you need to pass the entire array, use array subscript notation.");
            }
        }
        
        throw new CompilationException(null, null, "Variable " + varName + " not found");
    }
    
    /**
     * Converts a binary operation and wraps it in a Command.
     */
    private String convertBinOpToCommand(BinOpNode binOp, List<String> variableScope) 
            throws CompilationException {
        
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType(mapBinOp(binOp.getOp()));
        
        // Recursively convert left and right, each wrapped in commands
        String leftCommandId = convertExpressionToCommand(binOp.getLeft(), variableScope);
        String rightCommandId = convertExpressionToCommand(binOp.getRight(), variableScope);
        
        operation.setOperand1(leftCommandId);
        operation.setOperand2(rightCommandId);
        
        ruleEngineInput.getOperations().add(operation);
        
        // Wrap operation in command
        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        command.setOperation(operation.getId());
        ruleEngineInput.getCommands().add(command);
        
        return command.getId();
    }
    
    /**
     * Converts a unary operation and wraps it in a Command.
     * 
     * <p>Currently only handles negation of constants (-5, -3.14, etc.).
     * For constants, creates a negative constant and returns its ID.
     * Other unary operations are not yet supported.</p>
     * 
     * @param unaryOp The UnaryOpNode to convert
     * @param variableScope Current scope stack for operand resolution
     * @return Command containing the constant.
     * @throws CompilationException If operand is not a constant or operation is not supported
     */
    private Command  convertUnaryOpToCommand(UnaryOpNode unaryOp, List<String> variableScope) 
            throws CompilationException {
        
        // For now, only handle negation of constants
        if ("USub".equals(unaryOp.getOp())) {
            AstNode operand = unaryOp.getOperand();
            
            if (operand instanceof ConstantNode) {
                ConstantNode constantNode = (ConstantNode) operand;
                Object value = constantNode.getValue();
                Constant c = new Constant();
                
                // Create a negative constant
                if (value instanceof Integer) {
                    c.setId("const_" + UUID.randomUUID().toString());
                    c.setValue(-((Integer) value));
                    c.setDataType("Integer");
                    ruleEngineInput.getConstants().add(c);
                } else if (value instanceof Double) {
                    c.setId("const_" + UUID.randomUUID().toString());
                    c.setValue(-((Double) value));
                    c.setDataType("Double");
                    ruleEngineInput.getConstants().add(c);
                }

                Command command = new Command();
                command.setId("command_" + UUID.randomUUID().toString());
                command.setConstant(c.getId());
                ruleEngineInput.getCommands().add(command);
                return command;
            }
        }
        
        // TODO: Support other unary operations (!, ~) and non-constant operands
        throw new CompilationException(null, null, "Unary operation " + unaryOp.getOp() + " on non-constants not yet supported");
    }
    
    /**
     * Converts a subscript expression and wraps it in a Command.
     * 
     * <p>Handles both declared arrays and function parameters that are arrays.
     * Function parameters are stored as MethodDataTypeAgnosticArg and are converted
     * to Array when accessed with subscript notation.</p>
     */
    private String convertSubscriptToCommand(SubscriptNode subscript, List<String> variableScope) 
            throws CompilationException {
        
        // Extract array name and indices (handles nested subscripts like arr[x1][y1])
        // This method validates that:
        // - The base is a simple name (no complex expressions like arr[func()][0])
        // - Indices are simple names/constants (no function calls like arr[func()] or array elements like arr[someArray[i]])
        SubscriptExtractionResult extractionResult = extractArrayNameAndIndices(subscript, variableScope);
        String arrayVarName = extractionResult.arrayName;
        List<String> indices = extractionResult.indices;
        
        // Validate that all indices are resolvable (variable, constant, or array names)
        if (indices != null && !indices.isEmpty()) {
            for (String indexId : indices) {
                if (!isValidIndexId(indexId, variableScope)) {
                    throw new CompilationException(null, null, "Array index " + indexId + " is not a valid variable or constant");
                }
            }
        }
        
        // First check if it's a declared array
        Array array = getExistingArray(arrayVarName, variableScope);
        if (array != null) {
            Command command = new Command();
            command.setId("command_" + UUID.randomUUID().toString());
            ArrayCommand arrayCommand = new ArrayCommand();
            arrayCommand.setArrayId(array.getId());
            // Set indices from subscript
            if (indices != null && !indices.isEmpty()) {
                arrayCommand.setIndex(indices);
            }
            command.setArrayCommand(arrayCommand);
            ruleEngineInput.getCommands().add(command);
            return command.getId();
        }
        
        // Check if it's a function parameter (MethodDataTypeAgnosticArg) - convert to Array
        MethodDataTypeAgnosticArg methodArg = getExistingMethodArg(arrayVarName, variableScope);
        if (methodArg != null) {
            // Convert MethodArg to Array when used with subscript
            ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(methodArg);
            Array newArray = new Array();
            newArray.setId(methodArg.getId());
            newArray.setName(methodArg.getName());
            newArray.setDataType("array");
            
            ruleEngineInput.getArrays().add(newArray);
            String scope = getScope(variableScope);
            codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + arrayVarName);
            codeConverter.setArray(newArray, scope);
            
            Command command = new Command();
            command.setId("command_" + UUID.randomUUID().toString());
            ArrayCommand arrayCommand = new ArrayCommand();
            arrayCommand.setArrayId(newArray.getId());
            // Set indices from subscript
            if (indices != null && !indices.isEmpty()) {
                arrayCommand.setIndex(indices);
            }
            command.setArrayCommand(arrayCommand);
            ruleEngineInput.getCommands().add(command);
            return command.getId();
        }
        
        // Global fallback: try global scope when inside a function
        if (isInsideFunction(variableScope)) {
            Array globalArr = codeConverter.getArrayMap().get(arrayVarName);
            if (globalArr != null) {
                Command command = new Command();
                command.setId("command_" + UUID.randomUUID().toString());
                ArrayCommand arrayCommand = new ArrayCommand();
                arrayCommand.setArrayId(globalArr.getId());
                if (indices != null && !indices.isEmpty()) {
                    arrayCommand.setIndex(indices);
                }
                command.setArrayCommand(arrayCommand);
                ruleEngineInput.getCommands().add(command);
                return command.getId();
            }
        }
        
        throw new CompilationException(null, null, "Array or array parameter " + arrayVarName + " not found");
    }
    
    /**
     * Validates that an index ID references a known variable, constant, or array.
     * Converts method arguments to variables when encountered.
     */
    private boolean isValidIndexId(String indexId, List<String> variableScope) {
        // Check if it's a variable
        if (findVariableById(indexId) != null) {
            return true;
        }
        // Check if it's a constant
        if (findConstantById(indexId) != null) {
            return true;
        }
        // Check if it's an array
        if (findArrayById(indexId) != null) {
            return true;
        }
        // Check if it's a method argument
        MethodDataTypeAgnosticArg methodArg = findMethodArgById(indexId);
        if (methodArg != null) {
            // Convert MethodArg to Variable when first used
            ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(methodArg);
            Variable newVar = new Variable();
            newVar.setId(methodArg.getId());
            newVar.setName(methodArg.getName());
            newVar.setDataType("Integer"); // Default type, will be refined during operations
            
            ruleEngineInput.getVariables().add(newVar);
            String scope = getScope(variableScope);
            codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + methodArg.getName());
            codeConverter.setVariable(newVar, scope);
            return true;
        }
        return false;
    }
    
    /**
     * Converts a function call expression and wraps it in a Command.
     * 
     * <p>Note: This method is used when a function call appears as part of an expression
     * (e.g., in arithmetic operations). For assignment statements like `x = func()`,
     * the caller should use convertFunctionCallWithReturnTargets instead.</p>
     */
    private String convertCallExpressionToCommand(CallNode call, List<String> variableScope) 
            throws CompilationException {
        
        if (!(call.getFunc() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex function calls not supported");
        }
        
        NameNode funcName = (NameNode) call.getFunc();
        String functionName = funcName.getId();
        
        FunctionCall functionCall = new FunctionCall();
        functionCall.setId(functionName);  // Use actual function name, not random ID
        
        List<String> argumentIds = new ArrayList<>();
        for (AstNode arg : call.getArgs()) {
            // Use getArgumentId to get variable/array IDs directly, not wrapped in commands
            String argumentId = getArgumentId(arg, variableScope, false);
            argumentIds.add(argumentId);
        }
        functionCall.setArguments(argumentIds);
        
        // Don't add to ruleEngineInput.getFunctionCalls() - this is a call site, not a definition
        
        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        command.setFunctionCall(functionCall);
        ruleEngineInput.getCommands().add(command);
        
        return command.getId();
    }
    
    /**
     * Converts a list expression and wraps it in a Command.
     */
    private String convertListToCommand(ListNode list, List<String> variableScope) {
        // For now, return empty command - list literals need special handling
        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        ruleEngineInput.getCommands().add(command);
        return command.getId();
    }
    
    // ============================================================================
    // Helper Methods
    // ============================================================================
    
    /**
     * Gets the ID of an argument expression (variable, array, methodArg, constant, or unary op).
     *
     * <p>Used for function call arguments where we need just the entity ID,
     * not wrapped in a Command.</p>
     *
     * @param arg           The argument expression node (NameNode, ConstantNode, UnaryOpNode, etc.)
     * @param variableScope Current scope stack for variable resolution
     * @param forReturn     Only return can process superscripted arrays. Other callers cannot handle,
     *                      because arayCommand is not allowed in funcCall or in arrayIndex in the interpreter.
     * @return The ID of the variable, array, methodArg, constant, or unary operation
     * @throws CompilationException If argument type is not supported or variable not found
     */
    private String getArgumentId(AstNode arg, List<String> variableScope, boolean forReturn) throws CompilationException {
        // Handle constant literals as arguments (e.g., func(3, x))
        if (arg instanceof ConstantNode) {
            return createConstantForArgument((ConstantNode) arg);
        }
        
        // Handle unary operations as arguments (e.g., func(-x, ~y))
        if (arg instanceof UnaryOpNode) {
            return convertUnaryOpToCommand((UnaryOpNode) arg, variableScope).getConstant();
        }

        if (forReturn && arg instanceof SubscriptNode) {
            // Handle array subscripting for return statements only
            SubscriptNode subscript = (SubscriptNode) arg;
            return convertSubscriptToCommand(subscript, variableScope);
        }
        
        if (!(arg instanceof NameNode)) {
            throw new CompilationException(null, null, "Function argument must be a variable name, constant literal, or unary operation");
        }
        
        NameNode nameNode = (NameNode) arg;
        String varName = nameNode.getId();
        
        Variable variable = getExistingVariable(varName, variableScope);
        if (variable != null) {
            return variable.getId();
        }
        
        Array array = getExistingArray(varName, variableScope);
        if (array != null) {
            return array.getId();
        }
        
        MethodDataTypeAgnosticArg methodArg = getExistingMethodArg(varName, variableScope);
        if (methodArg != null) {
            return methodArg.getId();
        }
        
        // Global fallback: if we're inside a function and all scoped lookups failed,
        // try the global scope as a last resort. This allows functions to read global
        // variables/arrays that are NOT shadowed by a local or parameter of the same name.
        if (isInsideFunction(variableScope)) {
            Variable globalVar = codeConverter.getVariableMap().get(varName);
            if (globalVar != null) {
                return globalVar.getId();
            }
            Array globalArr = codeConverter.getArrayMap().get(varName);
            if (globalArr != null) {
                return globalArr.getId();
            }
        }
        
        throw new CompilationException(null, null, "Argument " + varName + " not found");
    }
    
    /**
     * Creates a Constant for use as a function argument.
     * 
     * <p>This creates a Constant entity without wrapping it in a Command,
     * since the constant is being used as an argument to a function call.</p>
     * 
     * @param constant The ConstantNode containing the literal value
     * @return The ID of the created Constant
     */
    private String createConstantForArgument(ConstantNode constant) {
        Constant c = new Constant();
        c.setId("const_" + UUID.randomUUID().toString());
        
        Object value = constant.getValue();
        // Set the actual value (numeric or string), not a string conversion
        c.setValue(value);
        if (value instanceof Integer) {
            c.setDataType("Integer");
        } else if (value instanceof Double) {
            c.setDataType("Double");
        } else {
            c.setDataType("String");
        }
        
        ruleEngineInput.getConstants().add(c);
        return c.getId();
    }
    
    /**
     * Infers the data type of a variable from its initial value.
     * 
     * <p>Used during variable declaration to determine the appropriate dataType field.
     * Examines the value node to decide between Integer, Double, String, or array.</p>
     * 
     * <h4>Inference Rules</h4>
     * <table border="1">
     *   <tr><th>Value Node</th><th>Value Type</th><th>Inferred Type</th><th>Example</th></tr>
     *   <tr><td>ConstantNode</td><td>Integer</td><td>"Integer"</td><td>x = 5</td></tr>
     *   <tr><td>ConstantNode</td><td>Double</td><td>"Double"</td><td>x = 3.14</td></tr>
     *   <tr><td>ConstantNode</td><td>String</td><td>"String"</td><td>x = "hello"</td></tr>
     *   <tr><td>ListNode</td><td>-</td><td>"array"</td><td>x = [1, 2, 3]</td></tr>
     *   <tr><td>BinOpNode</td><td>-</td><td>"Double"</td><td>x = a + b</td></tr>
     *   <tr><td>Other</td><td>-</td><td>"Integer"</td><td>Default fallback</td></tr>
     * </table>
     * 
     * <h4>Examples</h4>
     * <pre>
     * x = 42          → "Integer"
     * y = 3.14        → "Double"
     * name = "Alice"  → "String"
     * arr = [0 for _ in range(3)] → "array"
     * sum = a + b     → "Double" (assumes arithmetic produces floating point)
     * </pre>
     * 
     * @param value The value node being assigned to the variable
     * @return The inferred data type string
     */
    private String inferDataType(AstNode value) {
        if (value instanceof ConstantNode) {
            Object v = ((ConstantNode) value).getValue();
            if (v instanceof Integer) return "Integer";
            if (v instanceof Double) return "Double";
            return "String";
        } else if (value instanceof ListNode) {
            return "array";
        } else if (value instanceof ListCompNode) {
            // Only ListCompNode is valid for arrays
            return "array";
        } else if (value instanceof BinOpNode) {
            return "Double";
        }
        return "Integer";
    }
    
    // =========================================================================
    // OOP: class / method / object support
    // =========================================================================

    private void convertClassDef(ClassDefNode classDef, List<String> variableScope) throws CompilationException {
        String className = classDef.getName();
        ClassMeta classMeta = new ClassMeta();
        classMeta.className = className;

        List<AssignNode> fieldDecls = new ArrayList<>();
        List<FunctionDefNode> methods = new ArrayList<>();

        for (AstNode node : classDef.getBody()) {
            if (node instanceof AssignNode) {
                fieldDecls.add((AssignNode) node);
            } else if (node instanceof FunctionDefNode) {
                methods.add((FunctionDefNode) node);
            }
        }

        // Build ClassMeta field order
        for (AssignNode field : fieldDecls) {
            if (field.getTargets().isEmpty() || !(field.getTargets().get(0) instanceof NameNode)) continue;
            String fieldName = ((NameNode) field.getTargets().get(0)).getId();
            String dataType = inferDataType(field.getValue());
            if ("array".equals(dataType)) {
                classMeta.arrayFieldIndex.put(fieldName, classMeta.arrayFieldNames.size());
                classMeta.arrayFieldNames.add(fieldName);
            } else {
                classMeta.scalarFieldIndex.put(fieldName, classMeta.scalarFieldNames.size());
                classMeta.scalarFieldNames.add(fieldName);
            }
        }
        classRegistry.put(className, classMeta);

        String classScope = "class_" + className + "_";

        // Emit field IR entries scoped under "class_<ClassName>_"
        for (AssignNode field : fieldDecls) {
            if (field.getTargets().isEmpty() || !(field.getTargets().get(0) instanceof NameNode)) continue;
            String fieldName = ((NameNode) field.getTargets().get(0)).getId();
            String dataType = inferDataType(field.getValue());

            if ("array".equals(dataType)) {
                if (!(field.getValue() instanceof ListCompNode)) continue;
                Array array = new Array();
                array.setId("class_" + className + "_" + fieldName + "_arr");
                array.setName(fieldName);
                array.setDataType("array");
                List<String> classFieldScope = new ArrayList<>(variableScope);
                classFieldScope.add(classScope);
                List<Integer> constantDims = new ArrayList<>();
                boolean[] hasNonConstant = new boolean[]{false};
                extractArrayDimensionsFromListComp((ListCompNode) field.getValue(), classFieldScope, constantDims, hasNonConstant);
                if (!hasNonConstant[0]) array.setDimension(constantDims);
                ruleEngineInput.getArrays().add(array);
                codeConverter.setArray(array, classScope);
            } else {
                Variable variable = new Variable();
                variable.setId("class_" + className + "_" + fieldName + "_var");
                variable.setName(fieldName);
                variable.setDataType(dataType);
                if (field.getValue() instanceof ConstantNode) {
                    Object val = ((ConstantNode) field.getValue()).getValue();
                    if (val instanceof Number) variable.setValue(((Number) val).doubleValue());
                }
                ruleEngineInput.getVariables().add(variable);
                codeConverter.setVariable(variable, classScope);
            }
        }

        // Emit ClassDefinition IR entry
        ClassDefinition classDefinition = new ClassDefinition();
        classDefinition.setId(className);
        classDefinition.setClassName(className);
        classDefinition.setScalarFieldNames(new ArrayList<>(classMeta.scalarFieldNames));
        classDefinition.setArrayFieldNames(new ArrayList<>(classMeta.arrayFieldNames));
        ruleEngineInput.getClassDefinitions().add(classDefinition);

        // Compile methods
        String previousClassName = this.currentClassName;
        this.currentClassName = className;
        for (FunctionDefNode method : methods) {
            convertClassMethodDef(method, className, classMeta, variableScope);
        }
        this.currentClassName = previousClassName;
    }

    private void convertClassMethodDef(FunctionDefNode methodDef, String className, ClassMeta classMeta,
                                       List<String> outerScope) throws CompilationException {
        String methodId = className + "_" + methodDef.getName();

        int[] counter = new int[]{0};
        Map<Integer, RuleEngineInputUnits> variableFrameMap = new HashMap<>();

        // Scope: global → class scope → function scope
        List<String> variableScope = new ArrayList<>();
        variableScope.add("");
        variableScope.add("class_" + className + "_");
        variableScope.add("func_" + methodDef.getName() + "_");

        String previousFunctionName = this.currentFunctionName;
        this.currentFunctionName = methodDef.getName();

        List<String> paramIds = new ArrayList<>();

        for (ArgNode arg : methodDef.getArgs().getArgs()) {
            if ("self".equals(arg.getArg())) continue;
            MethodDataTypeAgnosticArg methodArg = new MethodDataTypeAgnosticArg();
            methodArg.setName(arg.getArg());
            methodArg.setFrameCount(counter[0]);
            methodArg.setId("arg_" + UUID.randomUUID().toString());
            paramIds.add(methodArg.getId());
            ruleEngineInput.getMethodDataTypeAgnosticArgs().add(methodArg);
            codeConverter.setMethodDataTypeAgnosticArgMap(methodArg, variableScope.get(variableScope.size() - 1));
            variableFrameMap.put(counter[0]++, methodArg);
        }

        FunctionCall functionCall = new FunctionCall();
        functionCall.setId(methodId);
        functionCall.setClassOwner(className);

        List<Command> bodyCommands = convertBody(methodDef.getBody(), variableScope, variableFrameMap, counter, functionCall);
        if (!bodyCommands.isEmpty()) {
            functionCall.setFirstCommandId(bodyCommands.get(0).getId());
        }

        functionCall.setArguments(paramIds);

        List<String> variablesInMethod = new ArrayList<>();
        int frameCounter = 0;
        while (true) {
            RuleEngineInputUnits units = variableFrameMap.get(frameCounter);
            if (units == null) break;
            variablesInMethod.add(units.getId());
            frameCounter++;
        }
        functionCall.setAllVariablesInMethod(variablesInMethod);

        ruleEngineInput.getFunctionCalls().add(functionCall);
        this.currentFunctionName = previousFunctionName;
    }

    private void convertMethodCall(CallNode call, Command command, List<String> variableScope)
            throws CompilationException {
        AttributeNode attr = (AttributeNode) call.getFunc();
        if (!(attr.getValue() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex method calls not supported");
        }
        String objVarName = ((NameNode) attr.getValue()).getId();
        String methodName = attr.getAttr();

        // self.method() inside a class method → recursive call on the same object
        if ("self".equals(objVarName) && currentClassName != null) {
            FunctionCall functionCall = new FunctionCall();
            functionCall.setId(currentClassName + "_" + methodName);
            functionCall.setObjectHandleId("__self__");
            List<String> argumentIds = new ArrayList<>();
            for (AstNode arg : call.getArgs()) {
                argumentIds.add(getArgumentId(arg, variableScope, false));
            }
            functionCall.setArguments(argumentIds);
            command.setFunctionCall(functionCall);
            debugLevelCodeCreator.concat("self." + methodName + "()");
            return;
        }

        String[] objInfo = codeConverter.getObjectInfo(objVarName);
        if (objInfo == null) {
            throw new CompilationException(null, null, "Unknown object variable: " + objVarName);
        }
        String objectHandleId = objInfo[0];
        String className = objInfo[1];

        FunctionCall functionCall = new FunctionCall();
        functionCall.setId(className + "_" + methodName);
        functionCall.setObjectHandleId(objectHandleId);

        List<String> argumentIds = new ArrayList<>();
        for (AstNode arg : call.getArgs()) {
            argumentIds.add(getArgumentId(arg, variableScope, false));
        }
        functionCall.setArguments(argumentIds);
        command.setFunctionCall(functionCall);
        debugLevelCodeCreator.concat(objVarName + "." + methodName + "()");
    }

    private void convertDelete(DeleteNode deleteNode, Command command, List<String> variableScope) {
        for (AstNode target : deleteNode.getTargets()) {
            if (!(target instanceof NameNode)) continue;
            String varName = ((NameNode) target).getId();
            String[] objInfo = codeConverter.getObjectInfo(varName);
            if (objInfo != null) {
                Command.DeleteObjectCommand doc = new Command.DeleteObjectCommand();
                doc.setObjectHandleId(objInfo[0]);
                command.setDeleteObjectCommand(doc);
                codeConverter.removeObject(varName);
                debugLevelCodeCreator.concat("del " + varName);
            }
        }
    }

    private boolean isInsideClassMethod(List<String> variableScope) {
        for (String scope : variableScope) {
            if (scope.startsWith("class_")) return true;
        }
        return false;
    }

    // =========================================================================

    /**
     * Looks up an existing Variable by name, respecting scope hierarchy.
     * 
     * <p>Searches from innermost scope outward, then checks global scope. This implements
     * proper variable shadowing where inner scopes can have variables with the same name
     * as outer scopes.</p>
     * 
     * <h4>Search Order Example</h4>
     * <pre>
     * variableScope = ["if_123", "while_456"]
     * name = "x"
     * 
     * Search order:
     *   1. "while_456x" (innermost scope)
     *   2. "if_123x" (outer scope)
     *   3. "x" (global scope)
     * </pre>
     * 
     * @param name The variable name to lookup
     * @param variableScope Current scope stack
     * @return The Variable object if found, null otherwise
     */
    private Variable getExistingVariable(String name, List<String> variableScope) {
        // Check if we're inside a function (any scope starts with "func_")
        boolean insideFunction = isInsideFunction(variableScope);
        
        for (int i = variableScope.size() - 1; i >= 0; i--) {
            String scope = variableScope.get(i);
            
            // If inside a function, skip global scope lookups
            // (In Python, assignment in a function creates a local variable unless 'global' is used)
            if (insideFunction && scope.isEmpty()) {
                continue;
            }
            
            Variable var = codeConverter.getVariableMap().get(scope + name);
            if (var != null) return var;
        }
        
        // If not inside a function, also check global scope
        if (!insideFunction) {
            return codeConverter.getVariableMap().get(name);
        }
        return null;
    }
    
    /**
     * Checks if the current scope is inside a function definition.
     * 
     * @param variableScope Current scope stack
     * @return true if inside a function, false otherwise
     */
    private boolean isInsideFunction(List<String> variableScope) {
        for (String scope : variableScope) {
            if (scope.startsWith("func_")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Looks up an existing Array by name, respecting scope hierarchy.
     * 
     * <p>Same scoping logic as getExistingVariable but searches the array map.</p>
     * 
     * @param name The array name to lookup
     * @param variableScope Current scope stack
     * @return The Array object if found, null otherwise
     */
    private Array getExistingArray(String name, List<String> variableScope) {
        // Check if we're inside a function
        boolean insideFunction = isInsideFunction(variableScope);
        
        for (int i = variableScope.size() - 1; i >= 0; i--) {
            String scope = variableScope.get(i);
            
            // If inside a function, skip global scope lookups for arrays
            if (insideFunction && scope.isEmpty()) {
                continue;
            }
            
            Array arr = codeConverter.getArrayMap().get(scope + name);
            if (arr != null) return arr;
        }
        
        // If not inside a function, also check global scope
        if (!insideFunction) {
            return codeConverter.getArrayMap().get(name);
        }
        return null;
    }
    
    /**
     * Looks up an existing method argument by name, respecting scope hierarchy.
     * 
     * <p>Used for function parameter resolution (currently minimal function support).</p>
     * 
     * @param name The argument name to lookup
     * @param variableScope Current scope stack
     * @return The MethodDataTypeAgnosticArg object if found, null otherwise
     */
    private MethodDataTypeAgnosticArg getExistingMethodArg(String name, List<String> variableScope) {
        for (int i = variableScope.size() - 1; i >= 0; i--) {
            String scope = variableScope.get(i);
            MethodDataTypeAgnosticArg arg = codeConverter.getMethodDataTypeAgnosticArgMap().get(scope + name);
            if (arg != null) return arg;
        }
        return codeConverter.getMethodDataTypeAgnosticArgMap().get(name);
    }
    
    /**
     * Gets the current scope ID for variable registration.
     * 
     * <p>Returns the innermost scope ID (last element in variableScope list) or empty
     * string for global scope. Used as prefix when creating new variables/arrays.</p>
     * 
     * <h4>Examples</h4>
     * <pre>
     * variableScope = [] → "" (global scope)
     * variableScope = ["if_123"] → "if_123"
     * variableScope = ["if_123", "while_456"] → "while_456" (innermost)
     * </pre>
     * 
     * @param variableScope Current scope stack
     * @return The current scope ID or empty string for global
     */
    private String getScopedId(List<String> variableScope) {
        return variableScope.isEmpty() ? "" : variableScope.get(variableScope.size() - 1);
    }
    
    /**
     * Gets the current scope for variable registration (identical to getScopedId).
     * 
     * @param variableScope Current scope stack
     * @return The current scope ID or empty string for global
     */
    private String getScope(List<String> variableScope) {
        return variableScope.isEmpty() ? "" : variableScope.get(variableScope.size() - 1);
    }
    
    /**
     * Maps Python AST binary operator names to RuleEngine operator symbols.
     * 
     * <h4>Operator Mapping</h4>
     * <pre>
     * AST Name → Symbol
     * "Add"    → "+"
     * "Sub"    → "-"
     * "Mult"   → "*"
     * "Div"    → "/"
     * "Mod"    → "%"
     * "Pow"    → "^"
     * </pre>
     * 
     * @param op The AST operator name (e.g., "Add", "Mult")
     * @return The operator symbol (e.g., "+", "*")
     */
    private String mapBinOp(String op) {
        switch (op) {
            case "Add": return "+";
            case "Sub": return "-";
            case "Mult": return "*";
            case "Div": return "/";
            case "Mod": return "%";
            case "Pow": return "^";
            default: return "+";
        }
    }
    
    /**
     * Maps Python AST comparison operator names to RuleEngine comparison symbols.
     * 
     * <h4>Operator Mapping</h4>
     * <pre>
     * AST Name  → Symbol
     * "Lt"      → "<"
     * "LtE"     → "<="
     * "Gt"      → ">"
     * "GtE"     → ">="
     * "Eq"      → "=="
     * "NotEq"   → "!="
     * </pre>
     * 
     * @param op The AST comparison operator name (e.g., "Gt", "Eq")
     * @return The comparison symbol (e.g., ">", "==")
     */
    private String mapCompareOp(String op) {
        switch (op) {
            case "Lt": return "<";
            case "LtE": return "<=";
            case "Gt": return ">";
            case "GtE": return ">=";
            case "Eq": return "==";
            case "NotEq": return "!=";
            default: return "==";
        }
    }
    
    /**
     * Maps Python AST operator names to Python source code symbols for debug output.
     * 
     * <h4>Operator Mapping</h4>
     * <pre>
     * AST Name → Python Symbol
     * "Add"    → "+"
     * "Sub"    → "-"
     * "Mult"   → "*"
     * "Div"    → "/"
     * </pre>
     * 
     * @param op The AST operator name
     * @return The Python operator symbol for debug output
     */
    private String opToPython(String op) {
        switch (op) {
            case "Add": return "+";
            case "Sub": return "-";
            case "Mult": return "*";
            case "Div": return "/";
            default: return "+";
        }
    }
    
    /**
     * Recursively appends a value expression to the debug code output.
     * 
     * <p>Traverses the expression tree and generates Python-like syntax for debug purposes.
     * Handles constants, variables, binary operations, and comparisons with proper formatting.</p>
     * 
     * <h4>Examples</h4>
     * <pre>
     * ConstantNode(42) → "42"
     * NameNode('x') → "x"
     * BinOpNode(Name('x'), Add, Constant(5)) → "x + 5"
     * BinOpNode(BinOp(a, Add, b), Mult, c) → "a + b * c"
     * CompareNode(Name('x'), Gt, Constant(10)) → "x > 10"
     * </pre>
     * 
     * @param value The expression node to append to debug output
     */
    private void appendValueToDebug(AstNode value) {
        if (value instanceof ConstantNode) {
            debugLevelCodeCreator.concat(String.valueOf(((ConstantNode) value).getValue()));
        } else if (value instanceof NameNode) {
            debugLevelCodeCreator.concat(((NameNode) value).getId());
        } else if (value instanceof BinOpNode) {
            BinOpNode binOp = (BinOpNode) value;
            appendValueToDebug(binOp.getLeft());
            debugLevelCodeCreator.concat(" " + opToPython(binOp.getOp()) + " ");
            appendValueToDebug(binOp.getRight());
        } else if (value instanceof CompareNode) {
            CompareNode compare = (CompareNode) value;
            appendValueToDebug(compare.getLeft());
            debugLevelCodeCreator.concat(" " + mapCompareOp(compare.getOps().get(0)) + " ");
            appendValueToDebug(compare.getComparators().get(0));
        }
    }
    
    /**
     * Extracts array dimensions from a ListNode, detecting both constant and variable dimensions.
     * Returns a list of dimension strings (either integer literals or variable IDs).
     * Also populates the constantDims list and sets the hasNonConstantDimension flag.
     */
    private List<String> extractArrayDimensions(ListNode listNode, List<String> variableScope,
                                                List<Integer> constantDims, boolean[] hasNonConstantDimension) 
            throws CompilationException {
        List<String> resolvedDims = new ArrayList<>();
        
        if (listNode == null || listNode.getElts().isEmpty()) {
            return resolvedDims;
        }
        
        // First dimension is the size of this list
        int size = listNode.getElts().size();
        constantDims.add(size);
        resolvedDims.add(String.valueOf(size));
        
        // Check if elements are nested lists (for multi-dimensional arrays)
        AstNode firstElt = listNode.getElts().get(0);
        if (firstElt instanceof ListNode) {
            // Recursively extract nested dimensions
            boolean[] hasNonConstant = new boolean[]{false};
            List<String> nestedDims = extractArrayDimensions((ListNode) firstElt, variableScope, constantDims, hasNonConstant);
            resolvedDims.addAll(nestedDims);
            if (hasNonConstant[0]) {
                hasNonConstantDimension[0] = true;
            }
        } else if (firstElt instanceof BinOpNode) {
            // Handle pattern like [0] * n for variable-length dimension
            BinOpNode binOp = (BinOpNode) firstElt;
            if ("Mult".equals(binOp.getOp())) {
                AstNode right = binOp.getRight();
                if (right instanceof ConstantNode) {
                    try {
                        int dim = Integer.parseInt(((ConstantNode) right).getValue().toString());
                        constantDims.add(dim);
                        resolvedDims.add(String.valueOf(dim));
                    } catch (NumberFormatException e) {
                        hasNonConstantDimension[0] = true;
                        constantDims.add(1);
                        resolvedDims.add(((ConstantNode) right).getValue().toString());
                    }
                } else if (right instanceof NameNode) {
                    String dimVarName = ((NameNode) right).getId();
                    String dimVarId = resolveDimensionVariable(dimVarName, variableScope);
                    if (dimVarId != null) {
                        hasNonConstantDimension[0] = true;
                        constantDims.add(1);
                        resolvedDims.add(dimVarId);
                    }
                }
            }
        }
        
        return resolvedDims;
    }
    
    /**
     * Extracts array dimensions from a ListComp (list comprehension), detecting both constant and variable dimensions.
     * Only accepts arrays initialized with 0.
     * Handles patterns like [[0 for _ in range(10)] for _ in range(10)] for 2D arrays.
     * Supports n-dimensional arrays with nested comprehensions.
     * 
     * Valid forms:
     * - 1D: [0 for _ in range(n)]
     * - 2D: [[0 for _ in range(m)] for _ in range(n)]
     * - nD: nested comprehensions with 0 as the innermost element
     */
    private List<String> extractArrayDimensionsFromListComp(ListCompNode listComp, List<String> variableScope,
                                                            List<Integer> constantDims, boolean[] hasNonConstantDimension) 
            throws CompilationException {
        List<String> resolvedDims = new ArrayList<>();
        
        if (listComp == null || listComp.getGenerators().isEmpty()) {
            return resolvedDims;
        }
        
        // Extract outer dimension from the range() call in the generator
        ComprehensionNode generator = listComp.getGenerators().get(0);
        AstNode iter = generator.getIter();
        
        // Expect: Call(func=Name('range'), args=[...])
        if (!(iter instanceof CallNode)) {
            throw new CompilationException(null, null, 
                "Array comprehension must use range() for iteration");
        }
        
        CallNode rangeCall = (CallNode) iter;
        if (!(rangeCall.getFunc() instanceof NameNode) || 
            !"range".equals(((NameNode) rangeCall.getFunc()).getId())) {
            throw new CompilationException(null, null, 
                "Array comprehension must use range() for iteration");
        }
        
        if (rangeCall.getArgs().isEmpty()) {
            throw new CompilationException(null, null, 
                "range() must have a size argument");
        }
        
        AstNode rangeArg = rangeCall.getArgs().get(0);
        
        if (rangeArg instanceof ConstantNode) {
            // Constant dimension: range(10)
            try {
                int dim = Integer.parseInt(((ConstantNode) rangeArg).getValue().toString());
                constantDims.add(dim);
                resolvedDims.add(String.valueOf(dim));
            } catch (NumberFormatException e) {
                throw new CompilationException(null, null, 
                    "range() argument must be an integer");
            }
        } else if (rangeArg instanceof NameNode) {
            // Variable dimension: range(n)
            String dimVarName = ((NameNode) rangeArg).getId();
            String dimVarId = resolveDimensionVariable(dimVarName, variableScope);
            if (dimVarId != null) {
                hasNonConstantDimension[0] = true;
                constantDims.add(1); // Placeholder
                resolvedDims.add(dimVarId);
            } else {
                throw new CompilationException(null, null, 
                    "Dimension variable '" + dimVarName + "' not found");
            }
        } else {
            throw new CompilationException(null, null, 
                "range() argument must be a constant integer or variable name");
        }
        
        // Extract inner dimensions from the element expression
        AstNode elt = listComp.getElt();
        
        // Handle nested list comprehension: [[0 for _ in range(m)] for _ in range(n)]
        if (elt instanceof ListCompNode) {
            // Recursively extract dimensions from nested comprehension
            boolean[] hasNonConstant = new boolean[]{false};
            List<String> nestedDims = extractArrayDimensionsFromListComp((ListCompNode) elt, variableScope, constantDims, hasNonConstant);
            resolvedDims.addAll(nestedDims);
            if (hasNonConstant[0]) {
                hasNonConstantDimension[0] = true;
            }
        }
        // Innermost element must be 0
        else if (elt instanceof ConstantNode) {
            Object value = ((ConstantNode) elt).getValue();
            if (!(value instanceof Integer) || ((Integer) value) != 0) {
                throw new CompilationException(null, null, 
                    "Arrays must be initialized with 0. Found: " + value);
            }
            // No more dimensions to add - this is the innermost level
        }
        else {
            throw new CompilationException(null, null, 
                "Arrays must be initialized with 0 using form: [0 for _ in range(size)] or nested comprehensions. " +
                "Unsupported element type: " + elt.getClass().getSimpleName());
        }
        
        return resolvedDims;
    }
    
    /**
     * Resolves a dimension variable name to its ID, converting MethodDataTypeAgnosticArg to Variable if needed.
     */
    private String resolveDimensionVariable(String dimVarName, List<String> variableScope) 
            throws CompilationException {
        Variable dimVar = getExistingVariable(dimVarName, variableScope);
        if (dimVar != null) {
            return dimVar.getId();
        }
        
        MethodDataTypeAgnosticArg methodArg = getExistingMethodArg(dimVarName, variableScope);
        if (methodArg != null) {
            // Convert MethodArg to Variable for use as dimension
            ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(methodArg);
            Variable newVar = new Variable();
            newVar.setId(methodArg.getId());
            newVar.setName(methodArg.getName());
            newVar.setDataType("Integer");
            
            ruleEngineInput.getVariables().add(newVar);
            String scope = getScope(variableScope);
            codeConverter.getMethodDataTypeAgnosticArgMap().remove(scope + dimVarName);
            codeConverter.setVariable(newVar, scope);
            
            return newVar.getId();
        }
        
        // Global fallback: try global scope when inside a function
        Variable globalVar = codeConverter.getVariableMap().get(dimVarName);
        if (globalVar != null) {
            return globalVar.getId();
        }
        
        return null;
    }
}

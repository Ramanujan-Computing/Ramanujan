package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;

import java.util.*;

public class PythonAstToRuleEngineInputConverter {
    
    private CodeConverter codeConverter;
    private RuleEngineInput ruleEngineInput;
    private DebugLevelCodeCreator debugLevelCodeCreator;
    private Map<Integer, RuleEngineInputUnits> functionFrameVariableMap;
    private Integer[] frameVariableCounterId;
    
    // Track variable types for inference
    private Map<String, String> inferredTypes = new HashMap<>();
    
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
    
    public List<Command> convert(ModuleNode module, List<String> variableScope) throws CompilationException {
        List<Command> commands = new ArrayList<>();
        Command previousCommand = null;
        
        for (AstNode node : module.getBody()) {
            Command command = convertStatement(node, variableScope);
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
    
    private Command convertStatement(AstNode node, List<String> variableScope) throws CompilationException {
        Command command = new Command();
        command.setId("command_" + UUID.randomUUID().toString());
        command.setCodeStrPtr(debugLevelCodeCreator.getLine());
        ruleEngineInput.getCommands().add(command);
        
        if (node instanceof AssignNode) {
            convertAssign((AssignNode) node, command, variableScope);
        } else if (node instanceof AugAssignNode) {
            convertAugAssign((AugAssignNode) node, command, variableScope);
        } else if (node instanceof IfNode) {
            convertIf((IfNode) node, command, variableScope);
        } else if (node instanceof WhileNode) {
            convertWhile((WhileNode) node, command, variableScope);
        } else if (node instanceof FunctionDefNode) {
            convertFunctionDef((FunctionDefNode) node, command, variableScope);
            return null; // Functions don't create commands in main flow
        } else if (node instanceof ExprNode) {
            convertExpr((ExprNode) node, command, variableScope);
        } else if (node instanceof ReturnNode) {
            return null; // Skip return statements in main flow
        }
        
        return command;
    }
    
    private void convertAssign(AssignNode assign, Command command, List<String> variableScope) 
            throws CompilationException {
        
        AstNode target = assign.getTargets().get(0);
        AstNode value = assign.getValue();
        
        if (target instanceof NameNode) {
            NameNode nameNode = (NameNode) target;
            String varName = nameNode.getId();
            
            Variable existingVar = getExistingVariable(varName, variableScope);
            Array existingArray = getExistingArray(varName, variableScope);
            
            if (existingVar == null && existingArray == null) {
                String dataType = inferDataType(value);
                
                if ("array".equals(dataType)) {
                    Array array = new Array();
                    array.setId(getScopedId(variableScope) + UUID.randomUUID().toString());
                    array.setName(varName);
                    array.setDataType("array");
                    
                    if (value instanceof ListNode) {
                        List<Integer> dims = new ArrayList<>();
                        dims.add(((ListNode) value).getElts().size());
                        array.setDimension(dims);
                    }
                    
                    ruleEngineInput.getArrays().add(array);
                    codeConverter.setArray(array, getScope(variableScope));
                    inferredTypes.put(varName, "array");
                    command.setArrayCommand(array.getId());
                } else {
                    Variable variable = new Variable();
                    variable.setId(getScopedId(variableScope) + UUID.randomUUID().toString());
                    variable.setName(varName);
                    variable.setDataType(dataType);
                    
                    ruleEngineInput.getVariables().add(variable);
                    codeConverter.setVariable(variable, getScope(variableScope));
                    inferredTypes.put(varName, dataType);
                    
                    Operation operation = createAssignmentOperation(variable.getId(), value, variableScope);
                    command.setOperationId(operation.getId());
                }
            } else {
                String targetId = existingVar != null ? existingVar.getId() : existingArray.getId();
                Operation operation = createAssignmentOperation(targetId, value, variableScope);
                command.setOperationId(operation.getId());
            }
            
            debugLevelCodeCreator.concat(varName + " = ");
            appendValueToDebug(value);
            debugLevelCodeCreator.nextLine();
            
        } else if (target instanceof SubscriptNode) {
            convertArrayAssignment((SubscriptNode) target, value, command, variableScope);
        }
    }
    
    private void convertAugAssign(AugAssignNode augAssign, Command command, List<String> variableScope) 
            throws CompilationException {
        
        AstNode target = augAssign.getTarget();
        String op = augAssign.getOp();
        AstNode value = augAssign.getValue();
        
        if (target instanceof NameNode) {
            NameNode nameNode = (NameNode) target;
            String varName = nameNode.getId();
            
            Variable variable = getExistingVariable(varName, variableScope);
            if (variable == null) {
                throw new CompilationException(null, null, 
                    "Variable " + varName + " not found for augmented assignment");
            }
            
            BinOpNode binOp = new BinOpNode();
            binOp.setLeft(target);
            binOp.setOp(op);
            binOp.setRight(value);
            
            Operation operation = createAssignmentOperation(variable.getId(), binOp, variableScope);
            command.setOperationId(operation.getId());
            
            debugLevelCodeCreator.concat(varName + " " + opToPython(op) + "= ");
            appendValueToDebug(value);
            debugLevelCodeCreator.nextLine();
        }
    }
    
    private void convertArrayAssignment(SubscriptNode target, AstNode value, Command command, 
                                       List<String> variableScope) throws CompilationException {
        
        if (!(target.getValue() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex array indexing not yet supported");
        }
        
        NameNode arrayName = (NameNode) target.getValue();
        String arrayVarName = arrayName.getId();
        
        Array array = getExistingArray(arrayVarName, variableScope);
        if (array == null) {
            throw new CompilationException(null, null, "Array " + arrayVarName + " not found");
        }
        
        debugLevelCodeCreator.concat(arrayVarName + "[");
        appendValueToDebug(target.getSlice());
        debugLevelCodeCreator.concat("] = ");
        appendValueToDebug(value);
        debugLevelCodeCreator.nextLine();
    }
    
    private void convertIf(IfNode ifNode, Command command, List<String> variableScope) 
            throws CompilationException {
        
        If ifBlock = new If();
        ifBlock.setId("if_" + UUID.randomUUID().toString());
        variableScope.add(ifBlock.getId());
        
        Condition condition = convertCondition(ifNode.getTest(), variableScope);
        ifBlock.setConditionId(condition.getId());
        
        debugLevelCodeCreator.concat("if ");
        appendValueToDebug(ifNode.getTest());
        debugLevelCodeCreator.concat(":");
        debugLevelCodeCreator.addIndentation();
        debugLevelCodeCreator.nextLine();
        
        List<Command> ifCommands = convertBody(ifNode.getBody(), variableScope);
        if (!ifCommands.isEmpty()) {
            ifBlock.setIfCommand(ifCommands.get(0).getId());
        }
        
        debugLevelCodeCreator.decrementIndentation();
        
        if (!ifNode.getOrelse().isEmpty()) {
            debugLevelCodeCreator.concat("else:");
            debugLevelCodeCreator.addIndentation();
            debugLevelCodeCreator.nextLine();
            
            List<Command> elseCommands = convertBody(ifNode.getOrelse(), variableScope);
            if (!elseCommands.isEmpty()) {
                ifBlock.setElseCommandId(elseCommands.get(0).getId());
            }
            
            debugLevelCodeCreator.decrementIndentation();
        }
        
        ruleEngineInput.getIfBlocks().add(ifBlock);
        command.setIfBlocks(ifBlock.getId());
        
        variableScope.remove(variableScope.size() - 1);
    }
    
    private void convertWhile(WhileNode whileNode, Command command, List<String> variableScope) 
            throws CompilationException {
        
        While whileBlock = new While();
        whileBlock.setId("while_" + UUID.randomUUID().toString());
        variableScope.add(whileBlock.getId());
        
        Condition condition = convertCondition(whileNode.getTest(), variableScope);
        whileBlock.setConditionId(condition.getId());
        
        debugLevelCodeCreator.concat("while ");
        appendValueToDebug(whileNode.getTest());
        debugLevelCodeCreator.concat(":");
        debugLevelCodeCreator.addIndentation();
        debugLevelCodeCreator.nextLine();
        
        List<Command> bodyCommands = convertBody(whileNode.getBody(), variableScope);
        if (!bodyCommands.isEmpty()) {
            whileBlock.setLoopCommand(bodyCommands.get(0).getId());
        }
        
        debugLevelCodeCreator.decrementIndentation();
        
        ruleEngineInput.getWhileBlocks().add(whileBlock);
        command.setWhileBlock(whileBlock.getId());
        
        variableScope.remove(variableScope.size() - 1);
    }
    
    private void convertFunctionDef(FunctionDefNode funcDef, Command command, List<String> variableScope) {
        debugLevelCodeCreator.concat("def " + funcDef.getName() + "(");
        List<String> paramNames = new ArrayList<>();
        for (ArgNode arg : funcDef.getArgs().getArgs()) {
            paramNames.add(arg.getArg());
        }
        debugLevelCodeCreator.concat(String.join(", ", paramNames));
        debugLevelCodeCreator.concat("):");
        debugLevelCodeCreator.nextLine();
    }
    
    private void convertExpr(ExprNode expr, Command command, List<String> variableScope) 
            throws CompilationException {
        
        AstNode value = expr.getValue();
        
        if (value instanceof CallNode) {
            CallNode call = (CallNode) value;
            
            if (call.getFunc() instanceof AttributeNode) {
                AttributeNode attr = (AttributeNode) call.getFunc();
                if ("append".equals(attr.getAttr()) && attr.getValue() instanceof NameNode) {
                    convertListAppend((NameNode) attr.getValue(), call, command, variableScope);
                    return;
                }
            }
            
            convertFunctionCall(call, command, variableScope);
        }
        
        debugLevelCodeCreator.nextLine();
    }
    
    private void convertListAppend(NameNode listName, CallNode call, Command command, 
                                   List<String> variableScope) {
        
        String arrayVarName = listName.getId();
        
        debugLevelCodeCreator.concat(arrayVarName + ".append(");
        if (!call.getArgs().isEmpty()) {
            appendValueToDebug(call.getArgs().get(0));
        }
        debugLevelCodeCreator.concat(")");
    }
    
    private void convertFunctionCall(CallNode call, Command command, List<String> variableScope) 
            throws CompilationException {
        
        if (!(call.getFunc() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex function calls not yet supported");
        }
        
        NameNode funcName = (NameNode) call.getFunc();
        String functionName = funcName.getId();
        
        debugLevelCodeCreator.concat(functionName + "(");
        boolean first = true;
        for (AstNode arg : call.getArgs()) {
            if (!first) debugLevelCodeCreator.concat(", ");
            appendValueToDebug(arg);
            first = false;
        }
        debugLevelCodeCreator.concat(")");
    }
    
    private List<Command> convertBody(List<AstNode> body, List<String> variableScope) 
            throws CompilationException {
        
        List<Command> commands = new ArrayList<>();
        Command previousCommand = null;
        
        for (AstNode node : body) {
            Command command = convertStatement(node, variableScope);
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
    
    private Condition convertCondition(AstNode test, List<String> variableScope) 
            throws CompilationException {
        
        if (test instanceof CompareNode) {
            CompareNode compare = (CompareNode) test;
            
            Condition condition = new Condition();
            condition.setId(UUID.randomUUID().toString());
            
            String op = compare.getOps().get(0);
            condition.setConditionType(mapCompareOp(op));
            
            String leftId = convertExpression(compare.getLeft(), variableScope);
            String rightId = convertExpression(compare.getComparators().get(0), variableScope);
            
            condition.setComparisionCommand1(leftId);
            condition.setComparisionCommand2(rightId);
            
            ruleEngineInput.getConditions().add(condition);
            return condition;
        }
        
        throw new CompilationException(null, null, "Unsupported condition type");
    }
    
    private Operation createAssignmentOperation(String targetId, AstNode value, 
                                               List<String> variableScope) throws CompilationException {
        
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType("=");
        operation.setOperand1(targetId);
        
        String valueId = convertExpression(value, variableScope);
        operation.setOperand2(valueId);
        
        ruleEngineInput.getOperations().add(operation);
        return operation;
    }
    
    private String convertExpression(AstNode expr, List<String> variableScope) 
            throws CompilationException {
        
        if (expr instanceof ConstantNode) {
            return convertConstant((ConstantNode) expr);
        } else if (expr instanceof NameNode) {
            return convertName((NameNode) expr, variableScope);
        } else if (expr instanceof BinOpNode) {
            return convertBinOp((BinOpNode) expr, variableScope);
        } else if (expr instanceof SubscriptNode) {
            return convertSubscript((SubscriptNode) expr, variableScope);
        } else if (expr instanceof CallNode) {
            return convertCallExpression((CallNode) expr, variableScope);
        } else if (expr instanceof ListNode) {
            return convertList((ListNode) expr, variableScope);
        }
        
        throw new CompilationException(null, null, "Unsupported expression type: " + expr.getClass().getSimpleName());
    }
    
    private String convertConstant(ConstantNode constant) {
        Constant c = new Constant();
        c.setId(UUID.randomUUID().toString());
        c.setValue(String.valueOf(constant.getValue()));
        
        Object value = constant.getValue();
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
    
    private String convertName(NameNode name, List<String> variableScope) throws CompilationException {
        String varName = name.getId();
        
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
        
        throw new CompilationException(null, null, "Variable " + varName + " not found");
    }
    
    private String convertBinOp(BinOpNode binOp, List<String> variableScope) 
            throws CompilationException {
        
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType(mapBinOp(binOp.getOp()));
        
        String leftId = convertExpression(binOp.getLeft(), variableScope);
        String rightId = convertExpression(binOp.getRight(), variableScope);
        
        operation.setOperand1(leftId);
        operation.setOperand2(rightId);
        
        ruleEngineInput.getOperations().add(operation);
        return operation.getId();
    }
    
    private String convertSubscript(SubscriptNode subscript, List<String> variableScope) 
            throws CompilationException {
        
        if (!(subscript.getValue() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex subscript not supported");
        }
        
        NameNode arrayName = (NameNode) subscript.getValue();
        String arrayVarName = arrayName.getId();
        
        Array array = getExistingArray(arrayVarName, variableScope);
        if (array == null) {
            throw new CompilationException(null, null, "Array " + arrayVarName + " not found");
        }
        
        return array.getId();
    }
    
    private String convertCallExpression(CallNode call, List<String> variableScope) 
            throws CompilationException {
        
        if (!(call.getFunc() instanceof NameNode)) {
            throw new CompilationException(null, null, "Complex function calls not supported");
        }
        
        NameNode funcName = (NameNode) call.getFunc();
        
        FunctionCall functionCall = new FunctionCall();
        functionCall.setId("funcCall_" + UUID.randomUUID().toString());
        functionCall.setName(funcName.getId());
        
        List<String> argIds = new ArrayList<>();
        for (AstNode arg : call.getArgs()) {
            argIds.add(convertExpression(arg, variableScope));
        }
        functionCall.setArgumentIds(argIds);
        
        ruleEngineInput.getFunctionCalls().add(functionCall);
        return functionCall.getId();
    }
    
    private String convertList(ListNode list, List<String> variableScope) {
        return "";
    }
    
    // Helper methods
    
    private String inferDataType(AstNode value) {
        if (value instanceof ConstantNode) {
            Object v = ((ConstantNode) value).getValue();
            if (v instanceof Integer) return "Integer";
            if (v instanceof Double) return "Double";
            return "String";
        } else if (value instanceof ListNode) {
            return "array";
        } else if (value instanceof BinOpNode) {
            return "Double";
        }
        return "Integer";
    }
    
    private Variable getExistingVariable(String name, List<String> variableScope) {
        for (int i = variableScope.size() - 1; i >= 0; i--) {
            String scope = variableScope.get(i);
            Variable var = codeConverter.getVariableMap().get(scope + name);
            if (var != null) return var;
        }
        return codeConverter.getVariableMap().get(name);
    }
    
    private Array getExistingArray(String name, List<String> variableScope) {
        for (int i = variableScope.size() - 1; i >= 0; i--) {
            String scope = variableScope.get(i);
            Array arr = codeConverter.getArrayMap().get(scope + name);
            if (arr != null) return arr;
        }
        return codeConverter.getArrayMap().get(name);
    }
    
    private MethodDataTypeAgnosticArg getExistingMethodArg(String name, List<String> variableScope) {
        for (int i = variableScope.size() - 1; i >= 0; i--) {
            String scope = variableScope.get(i);
            MethodDataTypeAgnosticArg arg = codeConverter.getMethodDataTypeAgnosticArgMap().get(scope + name);
            if (arg != null) return arg;
        }
        return codeConverter.getMethodDataTypeAgnosticArgMap().get(name);
    }
    
    private String getScopedId(List<String> variableScope) {
        return variableScope.isEmpty() ? "" : variableScope.get(variableScope.size() - 1);
    }
    
    private String getScope(List<String> variableScope) {
        return variableScope.isEmpty() ? "" : variableScope.get(variableScope.size() - 1);
    }
    
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
    
    private String opToPython(String op) {
        switch (op) {
            case "Add": return "+";
            case "Sub": return "-";
            case "Mult": return "*";
            case "Div": return "/";
            default: return "+";
        }
    }
    
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
}

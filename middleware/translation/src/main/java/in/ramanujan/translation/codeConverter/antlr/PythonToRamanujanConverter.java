package in.ramanujan.translation.codeConverter.antlr;

import in.ramanujan.enums.DataType;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

/**
 * Python3 to Ramanujan intermediate code converter using ANTLR listener pattern.
 * This class converts Python AST to the intermediate representation that the rule engine understands.
 */
public class PythonToRamanujanConverter extends Python3ParserBaseListener {
    
    private final RuleEngineInput ruleEngineInput;
    private final DebugLevelCodeCreator debugLevelCodeCreator;
    private final List<String> variableScope;
    private final Map<String, Variable> variableMap;
    private final Map<String, Array> arrayMap;
    private final List<Command> commands;
    
    public PythonToRamanujanConverter(RuleEngineInput ruleEngineInput, 
                                      DebugLevelCodeCreator debugLevelCodeCreator,
                                      List<String> variableScope,
                                      Map<String, Variable> variableMap,
                                      Map<String, Array> arrayMap) {
        this.ruleEngineInput = ruleEngineInput;
        this.debugLevelCodeCreator = debugLevelCodeCreator;
        this.variableScope = variableScope;
        this.variableMap = variableMap;
        this.arrayMap = arrayMap;
        this.commands = new ArrayList<>();
    }
    
    public List<Command> getCommands() {
        return commands;
    }
    
    /**
     * Create a new variable in the current scope
     */
    private Variable createVariable(String name, String dataType, Object value) {
        Variable variable = new Variable();
        variable.setId((variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "") +
                UUID.randomUUID().toString());
        variable.setName(name);
        variable.setDataType(dataType);
        variable.setValue(value);
        
        ruleEngineInput.getVariables().add(variable);
        variableMap.put(variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) + name : name, variable);
        
        return variable;
    }
    
    /**
     * Get or create a variable by name
     */
    private Variable getOrCreateVariable(String name) {
        String scopedName = variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) + name : name;
        Variable variable = variableMap.get(scopedName);
        
        if (variable == null) {
            // Auto-create variable with integer type (default)
            variable = createVariable(name, "integer", 0);
        }
        
        return variable;
    }
    
    /**
     * Create a command for variable assignment
     */
    private Command createAssignmentCommand(Variable variable, String operationId) {
        Command command = new Command();
        command.setId(UUID.randomUUID().toString());
        command.setVariableId(variable.getId());
        if (operationId != null) {
            command.setOperation(operationId);
        }
        
        ruleEngineInput.getCommands().add(command);
        commands.add(command);
        
        return command;
    }
    
    /**
     * Create an operation from binary expression
     */
    private Operation createOperation(String operator, String leftOperand, String rightOperand) {
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType(mapPythonOperatorToRamanujan(operator));
        operation.setOperand1(leftOperand);
        operation.setOperand2(rightOperand);
        
        ruleEngineInput.getOperations().add(operation);
        
        return operation;
    }
    
    /**
     * Map Python operators to Ramanujan operators
     */
    private String mapPythonOperatorToRamanujan(String pythonOp) {
        switch (pythonOp) {
            case "+": return "+";
            case "-": return "-";
            case "*": return "*";
            case "/": return "/";
            case "//": return "/";  // Integer division
            case "%": return "%";
            case "==": return "==";
            case "!=": return "!=";
            case "<": return "<";
            case "<=": return "<=";
            case ">": return ">";
            case ">=": return ">=";
            case "and": return "&&";
            case "or": return "||";
            case "not": return "!";
            default: return pythonOp;
        }
    }
    
    
    @Override
    public void enterExpr_stmt(Python3Parser.Expr_stmtContext ctx) {
        // Handle variable assignment: x = value
        if (ctx.getChildCount() >= 3 && "=".equals(ctx.getChild(1).getText())) {
            String varName = ctx.getChild(0).getText();
            String rightSide = ctx.getChild(2).getText();
            
            Variable variable = getOrCreateVariable(varName);
            
            // Check if right side is a simple number
            try {
                Object value = parseValue(rightSide);
                variable.setValue(value);
                createAssignmentCommand(variable, null);
                
                if (debugLevelCodeCreator != null) {
                    debugLevelCodeCreator.concat(varName + " = " + rightSide + ";");
                }
            } catch (NumberFormatException e) {
                // Right side is an expression - we'll need to handle this more complexly
                // For now, create a simple assignment command
                createAssignmentCommand(variable, null);
            }
        }
    }
    
    @Override
    public void enterWhile_stmt(Python3Parser.While_stmtContext ctx) {
        // Handle while loops: while condition:
        if (ctx.getChildCount() >= 3) {
            String conditionText = extractConditionText(ctx.getChild(1));
            
            // Create a While block
            While whileBlock = new While();
            whileBlock.setId(UUID.randomUUID().toString());
            
            // Parse the condition
            Condition condition = parseCondition(conditionText);
            if (condition != null) {
                whileBlock.setConditionId(condition.getId());
            }
            
            ruleEngineInput.getWhileBlocks().add(whileBlock);
            
            if (debugLevelCodeCreator != null) {
                debugLevelCodeCreator.concat("while(" + conditionText + ") {");
            }
        }
    }
    
    @Override
    public void exitWhile_stmt(Python3Parser.While_stmtContext ctx) {
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat("}");
        }
    }
    
    @Override
    public void enterIf_stmt(Python3Parser.If_stmtContext ctx) {
        // Handle if statements: if condition:
        if (ctx.getChildCount() >= 3) {
            String conditionText = extractConditionText(ctx.getChild(1));
            
            // Create an If block
            If ifBlock = new If();
            ifBlock.setId(UUID.randomUUID().toString());
            
            // Parse the condition
            Condition condition = parseCondition(conditionText);
            if (condition != null) {
                ifBlock.setConditionId(condition.getId());
            }
            
            ruleEngineInput.getIfBlocks().add(ifBlock);
            
            if (debugLevelCodeCreator != null) {
                debugLevelCodeCreator.concat("if(" + conditionText + ") {");
            }
        }
    }
    
    @Override
    public void exitIf_stmt(Python3Parser.If_stmtContext ctx) {
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat("}");
        }
    }
    
    @Override
    public void enterFuncdef(Python3Parser.FuncdefContext ctx) {
        // Handle function definition: def func_name(params):
        if (ctx.getChildCount() >= 4) {
            String funcName = ctx.getChild(1).getText();
            
            // Create a function call structure (Ramanujan uses this for function definitions)
            FunctionCall functionCall = new FunctionCall();
            functionCall.setId(UUID.randomUUID().toString());
            // Note: FunctionCall doesn't have setFunctionName, we'll use the id to track it
            
            ruleEngineInput.getFunctionCalls().add(functionCall);
            
            if (debugLevelCodeCreator != null) {
                debugLevelCodeCreator.concat("def " + funcName + "() {");
            }
        }
    }
    
    @Override
    public void exitFuncdef(Python3Parser.FuncdefContext ctx) {
        if (debugLevelCodeCreator != null) {
            debugLevelCodeCreator.concat("}");
        }
    }
    
    /**
     * Parse a value from string (integer, float, or string)
     */
    private Object parseValue(String valueStr) throws NumberFormatException {
        valueStr = valueStr.trim();
        
        // Try integer
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException e1) {
            // Try double
            try {
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException e2) {
                // Return as string (remove quotes if present)
                if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
                    return valueStr.substring(1, valueStr.length() - 1);
                } else if (valueStr.startsWith("'") && valueStr.endsWith("'")) {
                    return valueStr.substring(1, valueStr.length() - 1);
                }
                return valueStr;
            }
        }
    }
    
    /**
     * Extract condition text from a parse tree node
     */
    private String extractConditionText(org.antlr.v4.runtime.tree.ParseTree node) {
        if (node instanceof TerminalNode) {
            return node.getText();
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(extractConditionText(node.getChild(i)));
        }
        return sb.toString();
    }
    
    /**
     * Parse a condition string and create a Condition object
     */
    private Condition parseCondition(String conditionText) {
        // Simple condition parsing - look for comparison operators
        String[] operators = {"<=", ">=", "==", "!=", "<", ">"};
        
        for (String op : operators) {
            if (conditionText.contains(op)) {
                String[] parts = conditionText.split(op, 2);
                if (parts.length == 2) {
                    Condition condition = new Condition();
                    condition.setId(UUID.randomUUID().toString());
                    condition.setConditionType(mapPythonOperatorToRamanujan(op));
                    condition.setComparisionCommand1(parts[0].trim());
                    condition.setComparisionCommand2(parts[1].trim());
                    
                    ruleEngineInput.getConditions().add(condition);
                    return condition;
                }
            }
        }
        
        return null;
    }
}
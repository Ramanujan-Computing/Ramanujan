package in.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl;

import in.ramanujan.enums.ConditionType;
import in.ramanujan.enums.OperatorType;
import in.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.ramanujan.middleware.base.codeConverter.CodeConverterLogic;
import in.ramanujan.middleware.base.exception.CompilationException;
import in.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;
import in.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.middleware.base.utils.StringUtils;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/*
 * condition expected to be <operand> <operator> <operand>
 */
@Component
public class OperationLogicConverter implements CodeConverterLogic {

    @Autowired
    private StringUtils stringUtils;

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {
        if(command != null) {
            command.setOperation(ruleEngineInputUnits.getId());
        }
    }

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter,
                                            List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator,
                                            Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId)
            throws CompilationException {
        try {
            /*
            Operation operation = new Operation();
            operation.setId(UUID.randomUUID().toString());
             */
            code = code.replace(" ", "");
            debugLevelCodeCreator.concat(code + ";");

            List<String> postFixResult = getOperationParts(code);
            return evaluatePostFixAndRecursivelyResolveOp(postFixResult, ruleEngineInput, codeConverter, variableScope,
                    functionFrameVariableMap, frameVariableCounterId);

            /*
            List<String> operationParts = getOperationParts(code, "operation");
            operation.setOperatorType(operationParts.get(1));
            operation.setOperand1(codeConverter.interpret(operationParts.get(0), ruleEngineInput, variableScope, new NoConcatImpl(), functionFrameVariableMap, frameVariableCounterId).get(0).getId());
            operation.setOperand2(codeConverter.interpret(operationParts.get(2), ruleEngineInput, variableScope, new NoConcatImpl(), functionFrameVariableMap, frameVariableCounterId).get(0).getId());
            ruleEngineInput.getOperations().add(operation);
            return operation;

            */
        } catch (CompilationException e) {
            e.getMessageString().add(code);
            throw e;
        }
    }

    int x = 0;

    private RuleEngineInputUnits evaluatePostFixAndRecursivelyResolveOp(List<String> postFixResult, RuleEngineInput ruleEngineInput,
                                                             CodeConverter codeConverter, List<String> variableScope,
                                                             Map<Integer, RuleEngineInputUnits> functionFrameVariableMap,
                                                             Integer[] frameVariableCounterId) throws CompilationException {
        Stack<Command> stack = new Stack<>();
        RuleEngineInputUnits lastOp = null;
        for(String token : postFixResult) {
            if (isOperator(token)) {
                Command commandOp2 = stack.pop();
                Command commandOp1 = stack.pop();
                lastOp = getPostFixUnit(token, commandOp1.getId(), commandOp2.getId(), ruleEngineInput);

                Command command = new Command();
                command.setId(UUID.randomUUID().toString());
                setCommandPostFixUnit(command, lastOp);
                ruleEngineInput.getCommands().add(command);
                stack.push(command);
            } else {
                stack.push(codeConverter.interpret(token, ruleEngineInput, variableScope, new NoConcatImpl(),
                        functionFrameVariableMap, frameVariableCounterId).get(0));
            }
        }

        return lastOp;
    }

    protected void setCommandPostFixUnit(Command command, RuleEngineInputUnits commandPart) {
        command.setOperation(commandPart.getId());
    }

    protected RuleEngineInputUnits getPostFixUnit(String type, String operationLeft, String operationRight, RuleEngineInput ruleEngineInput) {
        Operation operation = new Operation();
        operation.setId(UUID.randomUUID().toString());
        operation.setOperatorType(type);
        operation.setOperand1(operationLeft);
        operation.setOperand2(operationRight);
        ruleEngineInput.getOperations().add(operation);
        return operation;
    }

    public StringUtils getStringUtils() {
        return stringUtils;
    }

    /**
     * Get precedence of math operations. Have all from OperatorType enum.
     * */
    private static int getPrecedence(String operation) {
        switch (operation) {
            case "+":
            case "-":
                return 4;
            case "*":
            case "/":
                return 5;
            case "=":
            case "==":
            case "!=":
            case ">":
            case "<":
            case ">=":
            case "<=":
                return 0;
            case "||":
                return 1;
            case "&&":
                return 2;
            case "(":
                return 3;

            default:
                return -1;

        }
    }

    // Improved: Only treat as operator if it's a single operator symbol, not a negative number or variable
    public static boolean isOperator(String operator) throws CompilationException {
        // Exclude negative numbers or variables (e.g., -1, -x)
        if (operator == null || operator.isEmpty()) return false;
        // If it starts with a digit or letter after '-', it's not an operator
        if (operator.length() > 1 && operator.charAt(0) == '-') {
            char next = operator.charAt(1);
            // Check all characters are digits or has only one '.'. If yes than its negative number and return false.
            boolean isNegNumOperator = true;
            int decimalCount = 0;
            for(int i=1;i< operator.length();i++) {
                if(!Character.isDigit(operator.charAt(i)) && operator.charAt(i) != '.') {
                    if(operator.charAt(i) != '.')
                    {
                        decimalCount++;
                    }
                    isNegNumOperator = false;
                }
            }
            if(decimalCount > 1)
            {
                throw new CompilationException(null, null, "invalid operand " + operator);
            }
            if(isNegNumOperator) {
                return false;
            }
        }
        // Only treat as operator if precedence is defined
        return getPrecedence(operator) != -1;
    }

    /**
     * do a kind of infix to postfix conversion
     *
     * ex: x1[thread][thisIter]=x1[thread][currentIter]+(x1[best][currentIter]-x1[thread][currentIter])/2
     * -> x1[thread][thisIter] x1[thread][currentIter] (x1[best][currentIter] - x1[thread][currentIter]) 2 / + =
     * */
    protected List<String> getOperationParts(String code) throws CompilationException {
        List<String> postFixResult = new ArrayList<>();
        Stack<String> stack = new Stack<>();
        int codeLen = code.length();
        StringBuilder operand = new StringBuilder();
        boolean expectUnary = true; // Track if a unary minus is expected
        for (int i = 0; i < codeLen; i++) {
            char c = code.charAt(i);
            if (c == ' ') {
                continue;
            }
            if (Character.isDigit(c) || Character.isAlphabetic(c) || c == '.' || c == '_') {
                operand.append(c);
                expectUnary = false;
            } else if (c == '[') {
                int j = i;
                int openBrackets = 1;
                while (j < codeLen && openBrackets > 0) {
                    j++;
                    if(code.charAt(j) == '[') {
                        openBrackets++;
                    } else if(code.charAt(j) == ']') {
                        openBrackets--;
                    }
                }
                if(j == codeLen) {
                    throw new CompilationException(null, null, "array not closed");
                }
                operand.append(code.substring(i, j + 1));
                i = j;
                expectUnary = false;
            } else {
                // Handle unary minus
                if (c == '-' && expectUnary) {
                    operand.append('-');
                    expectUnary = false;
                    continue;
                }
                if (operand.length() > 0) {
                    String operandStr = operand.toString();
                    if(operandStr.split(".").length > 2) {
                        throw new CompilationException(null, null, "invalid operand " + operandStr);
                    }
                    postFixResult.add(operandStr);
                    operand = new StringBuilder();
                }
                if (c == '(') {
                    int j = i;
                    int openBrackets = 1;
                    while (j < codeLen && openBrackets > 0) {
                        j++;
                        if(code.charAt(j) == '(') {
                            openBrackets++;
                        } else if(code.charAt(j) == ')') {
                            openBrackets--;
                        }
                    }
                    if(j == codeLen) {
                        throw new CompilationException(null, null, "bracket not closed");
                    }
                    postFixResult.add(code.substring(i + 1, j));
                    i = j;
                    expectUnary = true;
                } else {
                    StringBuilder stringBuilder = new StringBuilder().append(c);
                    if (i + 1 < codeLen && code.charAt(i + 1) == '=') {
                        stringBuilder.append("=");
                        i++;
                    }
                    String op = stringBuilder.toString();
                    if(OperatorType.getOperatorTypeInfo(op) == null && ConditionType.getConditionType(op) == null) {
                        throw new CompilationException(null, null, "unknown operation " + op);
                    }
                    while (!stack.isEmpty() && getPrecedence(stack.peek()) >= getPrecedence(op)) {
                        postFixResult.add(stack.pop());
                    }
                    stack.push(stringBuilder.toString());
                    expectUnary = true;
                }
            }
        }
        if(operand.length() > 0) {
            postFixResult.add(operand.toString());
        }
        while(!stack.isEmpty()) {
            postFixResult.add(stack.pop());
        }
        return postFixResult;
    }

}

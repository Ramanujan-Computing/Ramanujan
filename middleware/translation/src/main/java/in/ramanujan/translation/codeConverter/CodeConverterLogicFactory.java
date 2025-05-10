package in.ramanujan.translation.codeConverter;

import in.ramanujan.enums.ConditionType;
import in.ramanujan.enums.OperatorType;
import in.ramanujan.translation.codeConverter.codeConverterLogicImpl.*;
import in.ramanujan.translation.codeConverter.constants.CodeToken;

public class CodeConverterLogicFactory {
    private final IfLogicConverter ifLogicConverter = new IfLogicConverter();
    private final ForLogicConverter forLogicConverter = new ForLogicConverter();
    private final WhileLogicConverter whileLogicConverter = new WhileLogicConverter();
    private final FunctionLogicConverter functionLogicConverter = new FunctionLogicConverter();
    private final VariableInitLogicConverter variableInitLogicConverter = new VariableInitLogicConverter();
    private final ConditionLogicConverter conditionLogicConverter = new ConditionLogicConverter();
    private final OperationLogicConverter operationLogicConverter = new OperationLogicConverter();
    private final CsvImporter csvImporter = new CsvImporter();

    public CodeConverterLogic getCodeConverterLogicImpl(String token, String codeChunk) {
        if (token.equalsIgnoreCase("if"))
            return ifLogicConverter;
        if (token.equalsIgnoreCase("import_csv")) {
            return csvImporter;
        }
        if (token.equalsIgnoreCase("for"))
            return forLogicConverter;
        if (token.equalsIgnoreCase("while"))
            return whileLogicConverter;
        if (token.equalsIgnoreCase(CodeToken.functionExec)) {
            return functionLogicConverter;
        }
        if (token.equalsIgnoreCase("var"))
            return variableInitLogicConverter;
        if (isConditionOperation(codeChunk)) {
            return conditionLogicConverter;
        }
        if (isOperation(codeChunk)) {
            return operationLogicConverter;
        }
        return null;
    }

    private Boolean isOperation(String codeChunk) {
        if (codeChunk.charAt(0) == '-') {
            int index = 1;
            boolean isNegNumOperator = true;
            while (index < codeChunk.length() ) {
                char codeChar = codeChunk.charAt(index);
                if(!Character.isDigit(codeChar) && codeChar != '.') {
                    isNegNumOperator = false;
                    break;
                }
                index++;
            }
            if(isNegNumOperator) {
                return false;
            }
        }
        for (OperatorType operatorType : OperatorType.values()) {
            String op = operatorType.getOperatorCode();
            // Regex: operator not preceded by start, another operator, or ( or whitespace
            if(codeChunk.contains(op)) {
                return true;
            }
        }
        return false;
    }

    private Boolean isConditionOperation(String codeChunk) {
        for(ConditionType conditionType : ConditionType.values()) {
            if(codeChunk.contains(conditionType.getConditionTypeString())) {
                return true;
            }
        }
        return false;
    }
}

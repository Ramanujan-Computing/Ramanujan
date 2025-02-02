package in.robinhood.ramanujan.middleware.base.codeConverter;

import in.robinhood.ramanujan.enums.ConditionType;
import in.robinhood.ramanujan.enums.OperatorType;
import in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.*;
import in.robinhood.ramanujan.middleware.base.constants.CodeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CodeConverterLogicFactory {
    @Autowired
    private IfLogicConverter ifLogicConverter;

    @Autowired
    private ForLogicConverter forLogicConverter;

    @Autowired
    private WhileLogicConverter whileLogicConverter;

    @Autowired
    private FunctionLogicConverter functionLogicConverter;

    @Autowired
    private VariableInitLogicConverter variableInitLogicConverter;

    @Autowired
    private ConditionLogicConverter conditionLogicConverter;

    @Autowired
    private OperationLogicConverter operationLogicConverter;

    @Autowired
    private CsvImporter csvImporter;

    public CodeConverterLogic getCodeConverterLogicImpl(String token, String codeChunk) {
        if(token.equalsIgnoreCase("if"))
            return ifLogicConverter;
        if(token.equalsIgnoreCase("import_csv")) {
            return csvImporter;
        }
        if(token.equalsIgnoreCase("for"))
            return forLogicConverter;
        if(token.equalsIgnoreCase("while"))
            return whileLogicConverter;
        if(token.equalsIgnoreCase(CodeToken.functionExec)) {
            return functionLogicConverter;
        }
        if(token.equalsIgnoreCase("var"))
            return variableInitLogicConverter;
        if(isConditionOperation(codeChunk)) {
            return conditionLogicConverter;
        }
        if(isOperation(codeChunk)) {
            return operationLogicConverter;
        }
        return null;
    }

    private Boolean isOperation(String codeChunk) {
        for(OperatorType operatorType : OperatorType.values()) {
            if(codeChunk.contains(operatorType.getOperatorCode())) {
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

package in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.RuleEngineInputUnits;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/*
* condition expected to be <operand> <operator> <operand>
*/
@Component
public class ConditionLogicConverter extends OperationLogicConverter {

    @Autowired
    private StringUtils stringUtils;

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput,
                                            CodeConverter codeConverter, List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
        return super.convertCode(code, ruleEngineInput, codeConverter, variableScope, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId);
        /*
        try {
            Condition condition = new Condition();
            condition.setId(UUID.randomUUID().toString());
            code = code.replace(" ", "");
            debugLevelCodeCreator.concat(code + ";");
            List<String> conditionParts = getOperationParts(code, "condition");
            condition.setConditionType(conditionParts.get(1));
            condition.setComparisionCommand1(codeConverter.interpret(conditionParts.get(0), ruleEngineInput, variableScope, new NoConcatImpl(), functionFrameVariableMap, frameVariableCounterId).get(0).getId());
            condition.setComparisionCommand2(codeConverter.interpret(conditionParts.get(2), ruleEngineInput, variableScope, new NoConcatImpl(), functionFrameVariableMap, frameVariableCounterId).get(0).getId());
            ruleEngineInput.getConditions().add(condition);
            return condition;
        } catch (CompilationException e) {
            e.getMessageString().add(code);
            throw e;
        }
         */
    }

    @Override
    protected void setCommandPostFixUnit(Command command, RuleEngineInputUnits commandPart) {
        command.setConditionId(commandPart.getId());
    }

    @Override
    protected RuleEngineInputUnits getPostFixUnit(String type, String operationLeft, String operationRight, RuleEngineInput ruleEngineInput) {
        Condition condition = new Condition();
        condition.setId(UUID.randomUUID().toString());
        condition.setConditionType(type);
        condition.setComparisionCommand1(operationLeft);
        condition.setComparisionCommand2(operationRight);
        ruleEngineInput.getConditions().add(condition);
        return condition;
    }

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {
        command.setConditionId(ruleEngineInputUnits.getId());
    }


}

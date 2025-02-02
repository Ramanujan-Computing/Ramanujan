package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.enums.OperatorType;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Operation;
import in.ramanujan.rule.engine.factories.OperatorTypeFactory;
import in.ramanujan.rule.engine.functioning.OperatorFunctioning;
import in.ramanujan.rule.engine.functioning.operatorFunctioningImpl.CachedOperationFunctioning;
import lombok.Data;

import java.util.Map;

@Data
public class OperationRE extends RuleEngineInputUnit{
    private String operatorType;
    private CommandRE operand1;
    private CommandRE operand2;

    private OperatorFunctioning operatorFunctioning;

    private CachedOperationFunctioning cachedOperationFunctioning;

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        Operation operation = (Operation) ruleEngineInputUnitsBlock;
        id = operation.getId();
        operatorType = operation.getOperatorType();
        operand1 = (CommandRE) map.get(operation.getOperand1());
        operand2 = (CommandRE) map.get(operation.getOperand2());
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
        operatorFunctioning = OperatorTypeFactory.getOperatorFucntioningImpl(OperatorType.getOperatorTypeInfo(operatorType));
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new OperationRE();
    }
}

package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.enums.ConditionType;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.ramanujan.rule.engine.factories.ConditionTypeFactory;
import in.ramanujan.rule.engine.functioning.ConditionFunctioning;
import in.ramanujan.rule.engine.functioning.condtionFunctioningImpl.CachedConditionFunctioning;
import lombok.Data;

import java.util.Map;

@Data
public class ConditionRE extends RuleEngineInputUnit {
    private String conditionType;
    private CommandRE comparisionCommand1;
    private CommandRE comparisionCommand2;

    private ConditionFunctioning conditionFunctioning;

    private CachedConditionFunctioning cachedConditionFunctioning;

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        Condition condition = (Condition) ruleEngineInputUnitsBlock;
        id = condition.getId();
        conditionType = condition.getConditionType();
        comparisionCommand1 = (CommandRE) map.get(condition.getComparisionCommand1());
        comparisionCommand2 = (CommandRE) map.get(condition.getComparisionCommand2());
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
        conditionFunctioning = ConditionTypeFactory.getConditionFunctioningImpl(
                ConditionType.getConditionType(conditionType));
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new ConditionRE();
    }
}

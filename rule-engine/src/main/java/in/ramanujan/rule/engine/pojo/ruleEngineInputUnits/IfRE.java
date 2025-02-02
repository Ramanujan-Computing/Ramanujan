package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.If;
import lombok.Data;

import java.util.Map;

@Data
public class IfRE extends RuleEngineInputUnit{
    private ConditionRE conditionRE;
    private CommandRE ifCommandRE;
    private CommandRE elseCommandRE;



    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        If ifBlock = (If) ruleEngineInputUnitsBlock;
        id = ifBlock.getId();
        setConditionRE((ConditionRE) map.get(ifBlock.getConditionId()));
        setIfCommandRE((CommandRE) map.get(ifBlock.getIfCommand()));
        setElseCommandRE((CommandRE) map.get(ifBlock.getElseCommandId()));
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new IfRE();
    }
}

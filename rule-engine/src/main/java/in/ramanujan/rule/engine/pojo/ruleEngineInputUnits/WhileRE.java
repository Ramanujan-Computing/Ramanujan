package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.While;
import lombok.Data;

import java.util.Map;

@Data
public class WhileRE extends RuleEngineInputUnit {
    private ConditionRE condition;
    private CommandRE whileCommand;
    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        While whileBlock = (While) ruleEngineInputUnitsBlock;
        id = whileBlock.getId();
        condition = (ConditionRE) map.get(whileBlock.getConditionId());
        whileCommand = (CommandRE) map.get(whileBlock.getWhileCommandId());
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new WhileRE();
    }
}

package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.Map;

@Data
public abstract class RuleEngineInputUnit {
    protected String id;
    protected Integer codeStrPtr;

    public abstract void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map);

    public abstract RuleEngineInputUnit createNewObject();
}

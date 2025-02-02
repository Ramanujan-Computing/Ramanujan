package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;


import lombok.Data;

import java.util.Map;

@Data
public abstract class DataContainerRE extends RuleEngineInputUnit implements DataOperation, AbstractDataContainer {
    protected String name;
    protected String dataType;
}

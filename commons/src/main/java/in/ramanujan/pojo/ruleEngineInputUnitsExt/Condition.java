package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Condition extends RuleEngineInputUnits {
    private String conditionType;
    private String comparisionCommand1;
    private String comparisionCommand2;

    public Condition() {
        setClazz(Condition.class);
    }
}

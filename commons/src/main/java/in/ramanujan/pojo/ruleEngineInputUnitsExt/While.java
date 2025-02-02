package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class While extends RuleEngineInputUnits {
    private String conditionId;
    private String whileCommandId;

    public While() {
        setClazz(While.class);
    }
}

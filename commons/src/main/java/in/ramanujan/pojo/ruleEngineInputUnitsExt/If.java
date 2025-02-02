package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class If extends RuleEngineInputUnits {
    private String conditionId;
    private String ifCommand;
    private String elseCommandId;

    public If() {
        setClazz(If.class);
    }
}

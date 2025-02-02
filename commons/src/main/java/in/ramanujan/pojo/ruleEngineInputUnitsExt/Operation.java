package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Operation extends RuleEngineInputUnits {
    private String operatorType;
    private String operand1;
    private String operand2;

    public Operation() {
        setClazz(Operation.class);
    }
}

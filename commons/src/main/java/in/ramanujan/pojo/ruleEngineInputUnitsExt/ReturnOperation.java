package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReturnOperation extends RuleEngineInputUnits {
    private String operatorType;
    private String operand1;
    private String operand2;

    public ReturnOperation() {
        setClazz(ReturnOperation.class);
    }
}

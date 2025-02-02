package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayOptions;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Variable extends RuleEngineInputUnits {
    private String name;
    private String dataType;
    private Object value;
    private boolean isReturnable;
    /**
     * If variable part of function, this will tell what is the counter of the argument.
     * For ex: func(arg0, arg1) -> arg0 is 0; arg1 is 1
     */
    private Integer frameCount;

    public Variable() {
        setClazz(Variable.class);
    }
}

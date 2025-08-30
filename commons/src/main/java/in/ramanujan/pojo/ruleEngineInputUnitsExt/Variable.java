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
    
    /**
     * Sequence number in local frame. -1 if not in local frame.
     * This enables variable resolution by position in local frame similar to CPython.
     */
    private Integer localSequence = -1;
    
    /**
     * Sequence number in global frame. -1 if not in global frame.
     * This enables variable resolution by position in global frame similar to CPython.
     */
    private Integer globalSequence = -1;

    public Variable() {
        setClazz(Variable.class);
    }
}

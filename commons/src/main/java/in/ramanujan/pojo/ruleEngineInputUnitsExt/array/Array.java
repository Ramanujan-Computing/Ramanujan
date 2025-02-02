package in.ramanujan.pojo.ruleEngineInputUnitsExt.array;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Array extends RuleEngineInputUnits {
    private String name;
    private String dataType;
    private Map<String, Object> values;
    private List<Integer> dimension = new ArrayList<>();
    private boolean isReturnable;

    /**
     * If variable part of function, this will tell what is the counter of the argument.
     * For ex: func(arg0, arg1) -> arg0 is 0; arg1 is 1
     */
    private Integer frameCount;

    public Array() {
        values = new HashMap<>();
        setClazz(Array.class);
    }
}

package in.ramanujan.pojo.ruleEngineInputUnitsExt.array;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Array extends RuleEngineInputUnits {
    private String name;
    private String dataType;
    private Map<String, Object> values;
    private List<Integer> dimension = new ArrayList<>();
    private boolean isReturnable;

    /**
     * Path to a binary file containing flat float32 values (row-major).
     * When set, the native side loads data from this file instead of
     * deserializing the values map from JSON. The values map will be
     * empty in the serialized JSON to avoid huge payloads.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String binaryFile;

    /**
     * If variable part of function, this will tell what is the counter of the argument.
     * For ex: func(arg0, arg1) -> arg0 is 0; arg1 is 1
     */
    private Integer frameCount;

    public Array() {
        values = new ConcurrentHashMap<>();
        setClazz(Array.class);
    }
}

package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectHandleArg extends RuleEngineInputUnits {
    private String className;
    private int frameCount;

    public ObjectHandleArg() {
        setClazz(ObjectHandleArg.class);
    }
}

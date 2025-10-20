package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MethodDataTypeAgnosticArg  extends RuleEngineInputUnits {
    public String name;
    public int frameCount;
}

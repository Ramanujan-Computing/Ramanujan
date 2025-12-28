package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunctionCall extends RuleEngineInputUnits {
    private List<String> arguments;
    private String firstCommandId;
    private List<String> allVariablesInMethod;
    private List<String> returnTargetIds;

    public FunctionCall() {
        setClazz(FunctionCall.class);
    }
}

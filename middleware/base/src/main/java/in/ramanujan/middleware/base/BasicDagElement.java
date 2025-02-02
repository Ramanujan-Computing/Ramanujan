package in.ramanujan.middleware.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInput;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BasicDagElement {
    private String id;
    private RuleEngineInput ruleEngineInput;
    private String firstCommandId;
    private String commaSeparatedDebugPoints;
}

package in.robinhood.ramanujan.middleware.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BasicDagElement {
    private String id;
    private RuleEngineInput ruleEngineInput;
    private String firstCommandId;
    private String commaSeparatedDebugPoints;
}

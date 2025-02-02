package in.robinhood.ramanujan.orchestrator.base.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.checkpoint.Checkpoint;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncTask {
    private String uuid;
    private String status;
    private String hostAssigned;
    private RuleEngineInput ruleEngineInput;
    private Checkpoint checkpoint;
    private String firstCommandId;
    private Object data;
    private Boolean debug;
    private List<Integer> breakpoints;

    public AsyncTask(){}
}

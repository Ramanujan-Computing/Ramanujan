package in.ramanujan.orchestrator.base.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.pojo.storage.ResultUploadTarget;
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
    private ResultUploadTarget resultUpload;

    public AsyncTask(){}

    public AsyncTask(String uuid, String status, String hostAssigned, RuleEngineInput ruleEngineInput,
                     Checkpoint checkpoint, String firstCommandId, Object data, Boolean debug,
                     List<Integer> breakpoints) {
        this.uuid = uuid;
        this.status = status;
        this.hostAssigned = hostAssigned;
        this.ruleEngineInput = ruleEngineInput;
        this.checkpoint = checkpoint;
        this.firstCommandId = firstCommandId;
        this.data = data;
        this.debug = debug;
        this.breakpoints = breakpoints;
    }
}

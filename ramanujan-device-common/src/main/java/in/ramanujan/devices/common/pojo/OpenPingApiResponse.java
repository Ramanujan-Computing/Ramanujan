package in.ramanujan.devices.common.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenPingApiResponse {
    private String uuid;
    private String status;
    private String hostAssigned;
    private RuleEngineInput ruleEngineInput;
    private Checkpoint checkpoint;
    private String firstCommandId;
    private Object data;
    private Boolean debug;
    private List<Integer> breakpoints;
}

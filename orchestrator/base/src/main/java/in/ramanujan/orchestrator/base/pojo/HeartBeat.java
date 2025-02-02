package in.ramanujan.orchestrator.base.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartBeat {
    private String hostId;
    private Object data;
    private Long heartBeatTimeEpoch;
}

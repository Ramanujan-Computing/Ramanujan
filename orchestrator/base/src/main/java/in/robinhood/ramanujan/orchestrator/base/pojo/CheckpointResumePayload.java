package in.robinhood.ramanujan.orchestrator.base.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckpointResumePayload {
    private List<Integer> lines;
}

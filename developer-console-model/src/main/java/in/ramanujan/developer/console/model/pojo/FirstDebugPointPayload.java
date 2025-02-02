package in.ramanujan.developer.console.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FirstDebugPointPayload {
    private List<DagElementIdDebugPointPair> dagElementIdDebugPointPairs;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class DagElementIdDebugPointPair {
        private String dagElementId;
        private String commaSeparatedPoints;
    }
}

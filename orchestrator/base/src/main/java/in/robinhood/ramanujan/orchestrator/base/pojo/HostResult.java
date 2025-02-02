package in.robinhood.ramanujan.orchestrator.base.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostResult {
    private Map<String, Object> map;
}

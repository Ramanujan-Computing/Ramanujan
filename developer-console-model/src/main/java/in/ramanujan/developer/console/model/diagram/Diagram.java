package in.ramanujan.developer.console.model.diagram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Diagram {
    private Map<String, ThreadLevelDiagram> threadLevelDiagramMap;
    private String entryLevelThreadId;

    public Diagram() {
        threadLevelDiagramMap = new HashMap<>();
    }
}

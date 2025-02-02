package in.ramanujan.developer.console.model.diagram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.developer.console.model.diagram.parts.Condition;
import in.ramanujan.developer.console.model.diagram.parts.Operation;
import lombok.Data;

import java.util.*;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThreadLevelDiagram {
    private String id;
    private String name;
    private String diagramRoot;
    private Map<String, Operation> diagramPartOperationMap;
    private Map<String, Condition> diagramPartConditionMap;
    private List<String> nextThreadIds;

    public ThreadLevelDiagram() {
        id = UUID.randomUUID().toString();
        name = "";
        diagramPartOperationMap = new HashMap<>();
        diagramPartConditionMap = new HashMap<>();
        nextThreadIds = new ArrayList<>();
    }
}

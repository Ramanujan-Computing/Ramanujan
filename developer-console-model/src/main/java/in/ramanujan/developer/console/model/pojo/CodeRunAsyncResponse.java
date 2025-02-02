package in.ramanujan.developer.console.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.developer.console.model.diagram.Diagram;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeRunAsyncResponse {
    private String asyncId;
    private Diagram diagram;
    private String firstDagElementId;
    private DagElementIdGraph dagElementIdGraph;
}

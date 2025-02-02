package in.ramanujan.developer.console.model.diagram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiagramPart {
    private String id;
    private String nextId;
    private DiagramPartType diagramPartType;

    public DiagramPart() {
        id = UUID.randomUUID().toString();
        diagramPartType = DiagramPartType.OPERATION;
    }
}

package in.ramanujan.developer.console.model.diagram.parts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.developer.console.model.diagram.DiagramPart;
import in.ramanujan.developer.console.model.diagram.DiagramPartType;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Operation extends DiagramPart {
    private String tag;
    public Operation() {
        setDiagramPartType(DiagramPartType.OPERATION);
        setId(UUID.randomUUID().toString());
    }
}

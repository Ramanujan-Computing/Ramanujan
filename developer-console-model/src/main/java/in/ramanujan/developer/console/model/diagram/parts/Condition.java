package in.ramanujan.developer.console.model.diagram.parts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.developer.console.model.diagram.DiagramPart;
import in.ramanujan.developer.console.model.diagram.DiagramPartType;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Condition extends DiagramPart {
    private String ifNextId;
    private String ifNextTag;
    private String elseNextId;
    private String elseNextTag;

    public Condition() {
        setDiagramPartType(DiagramPartType.CONDITION);
        setId(UUID.randomUUID().toString());
    }
}

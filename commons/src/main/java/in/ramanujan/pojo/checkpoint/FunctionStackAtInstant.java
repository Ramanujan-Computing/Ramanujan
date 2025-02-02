package in.ramanujan.pojo.checkpoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.ContextStack;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunctionStackAtInstant {
    private String ruleEngineUnitId;
    private ContextStack contextStack;
}

package in.ramanujan.pojo.ruleEngineInputUnitsExt.array;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArrayCommand {
    private String arrayId;
    private List<String> index;
}

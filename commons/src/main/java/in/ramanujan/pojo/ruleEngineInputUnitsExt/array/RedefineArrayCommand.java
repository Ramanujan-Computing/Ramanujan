package in.ramanujan.pojo.ruleEngineInputUnitsExt.array;

import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;
import java.util.List;

@Data
public class RedefineArrayCommand extends RuleEngineInputUnits {
    private String id;
    private String arrayId;
    private List<String> newDimensions;
}

package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayCommand;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Command extends RuleEngineInputUnits {
    private String nextId;
    private String ifBlocks;
    private String loops;
    private String operation;
    private String constant;
    private String variableId;
    private String conditionId;
    private List<String> nextDagTriggerIds;
    private ArrayCommand arrayCommand;
    private FunctionCall functionCall;
    private String whileId;

    public Command() {
        setClazz(Command.class);
    }
}

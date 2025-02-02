package in.ramanujan.pojo.checkpoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.ContextStack;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Checkpoint {
    private List<FunctionStackAtInstant> commandStack;
    private Object data;
    private int size;

    public Checkpoint() {
        commandStack = new ArrayList<>();
        size = 0;
    }

    public void addStack(String ruleEngineUnitId, ContextStack contextStack) {
        FunctionStackAtInstant functionStackAtInstant = new FunctionStackAtInstant();
        functionStackAtInstant.setContextStack(contextStack);
        functionStackAtInstant.setRuleEngineUnitId(ruleEngineUnitId);
        commandStack.add(functionStackAtInstant);
        size ++;
    }

    public FunctionStackAtInstant pop() {
        if(size == 0) {
            return null;
        }
        FunctionStackAtInstant functionStackAtInstant = commandStack.get(size - 1);
        commandStack.remove(size - 1);
        size--;
        return functionStackAtInstant;
    }
}

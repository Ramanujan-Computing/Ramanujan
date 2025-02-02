package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE;

import lombok.Data;

import java.util.*;

@Data
public class VariableValue {
    public Double val;
    private final Set<String> connectedVariableIds = new HashSet<>();
    private final String uuid = UUID.randomUUID().toString();
    private final int hashcode = uuid.hashCode();
    public VariableValue(Double val, String originalVariableId) {
        this.val = val;
        this.connectedVariableIds.add(originalVariableId);
    }

    public void addConnectedVariable(String variableId) {
        connectedVariableIds.add(variableId);
    }

    public void removeConnectedVariable(String variableId) {
        connectedVariableIds.remove(variableId);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof VariableValue)) {
            return false;
        }
        return uuid.equals(((VariableValue) obj).getUuid());
    }
}

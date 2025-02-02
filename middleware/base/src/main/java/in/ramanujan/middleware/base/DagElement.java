package in.ramanujan.middleware.base;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.utils.Constants;
import lombok.Data;

import java.util.*;

@Data
public class DagElement extends BasicDagElement{
    private List<DagElement> previousElements;
    private List<DagElement> nextElements;
    private Set<String> previousElementIds;
    private Map<String, Variable> variableMap;
    private Map<String, Array> arrayMap;


    private String uuid = UUID.randomUUID().toString();

    //equals and hashcode method


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DagElement that = (DagElement) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    public DagElement(RuleEngineInput ruleEngineInput) {
        setId(UUID.randomUUID().toString());
        variableMap = new HashMap<>();
        arrayMap = new HashMap<>();
        setRuleEngineInput(ruleEngineInput);
        previousElementIds = new HashSet<>();
        previousElements = new ArrayList<>();
        nextElements = new ArrayList<>();

        if(ruleEngineInput.getVariables() != null) {
            for(Variable variable : ruleEngineInput.getVariables()) {
                variableMap.put(variable.getId(), variable);
            }
        }

        if(ruleEngineInput.getArrays() != null) {
            for(Array array : ruleEngineInput.getArrays()) {
                arrayMap.put(array.getId(), array);
            }
        }
    }

    public void refreshVariableValues(Map<String, Object> variableResult) {
        for(String variableId : variableResult.keySet()) {
            if(Constants.arrayIndex.equalsIgnoreCase(variableId)) {
                refreshArrayValues((Map)variableResult.get(variableId));
                continue;
            }
            Object variableValue = variableResult.get(variableId);
            if(variableMap.get(variableId) != null) {
                variableMap.get(variableId).setValue(variableValue);
            }
        }
    }

    private void refreshArrayValues(Map<String, Map<String, Object>> changelog) {
        for(String arrayId : changelog.keySet()) {
            Map<String, Object> valueMap = changelog.get(arrayId);
            Array array = arrayMap.get(arrayId);
            if(array != null && valueMap != null) {
                for(String index : valueMap.keySet()) {
                    array.getValues().put(index, valueMap.get(index));
                }
            }
        }
    }

}

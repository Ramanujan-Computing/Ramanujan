package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableValue;


import java.util.*;

public class VariableManager {
    private static Map<String, Set<VariableValue>> changeLog = new HashMap<>();

    public static Map<String, Object> getChangeLog(String processId) {
        Set<VariableValue> values = changeLog.get(processId);
        if(values == null) {
            return new HashMap<>();
        }
        Map<String, Object> log = new HashMap<>();
        for(VariableValue value : values) {
            Set<String> connectedVariableIds = value.getConnectedVariableIds();
            for(String connectedVarId : connectedVariableIds) {
                log.put(connectedVarId, value.getVal());
            }
        }
        return log;
    }

    public static void updateChangeLog(String processId, VariableValue value) {
        Set<VariableValue> changeLogForProcess = changeLog.get(processId);
        if(changeLogForProcess == null) {
            changeLogForProcess = new HashSet<>();
            changeLog.put(processId, changeLogForProcess);
        }
        changeLogForProcess.add(value);
    }

    private static Object changeVariableState(Map<String, Object> variableMap, String variableId, Object newValue) {
        return variableMap.put(variableId, newValue);
    }

    private static Object getVariableState(Map<String, Object> variableMap, String variableId) {
        return variableMap.get(variableId);
    }

    public static void removeProcess(String processId) {
        if(changeLog != null) {
            changeLog.remove(processId);
        }
    }

}

package in.ramanujan.rule.engine.manager;

import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableValue;
import in.ramanujan.utils.Constants;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ResultManager {
    public static Map<String, Object> getResultMap(String processId) {
        Map<String, Object> returnMap = new HashMap<>();
        returnMap.putAll(VariableManager.getChangeLog(processId));
        Map<String, Map<String, Object>> changeLog = ArrayManager.getChangeLog(processId);
        if(changeLog != null) {
            returnMap.put(Constants.arrayIndex, changeLog);
        }
        returnMap.putAll(NextDagTriggerManager.getNextTriggerMap(processId));
        return returnMap;
    }

    public static Map<String, Object> getResultMapForCheckpoint(String processId) {
        Map<String, Object> returnMap = VariableManager.getChangeLog(processId);
        Map<String, Map<String, Object>> changeLog = ArrayManager.getChangeLog(processId);
        if(changeLog != null) {
            returnMap.put(Constants.arrayIndex, changeLog);
        }
        return returnMap;
    }

    public static void initVariableMap(String processId, Map<String, Object> mapFromCheckpoint) {
        if(mapFromCheckpoint == null) {
            return;
        }
        Map<String, Object> variableMap  = new HashMap<>();
        for(String key : mapFromCheckpoint.keySet()) {
            if(Constants.arrayIndex.equalsIgnoreCase(key)) {
//                ArrayManager.populateArrayMap(processId, (Map<String, Map<String, Object>>)mapFromCheckpoint.get(Constants.arrayIndex));
            } else {
                variableMap.put(key, mapFromCheckpoint.get(key));
            }
        }
//        VariableManager.populateVariableMap(processId, variableMap);
    }
}

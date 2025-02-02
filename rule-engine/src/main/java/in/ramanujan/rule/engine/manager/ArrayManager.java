package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayValDataContainer;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayValue;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableValue;
import in.ramanujan.utils.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ArrayManager {
    /*
    * processArrayMap is the map required for the general computation of the input
    * changeLog is the map which contains only those indexes which have been updated by the computation.
    * */
    //TODO: changelog to have only the changed values. would have to think about it.....
    private static Map<String, Set<ArrayValue>> changeLog = new HashMap<>();

    public static long time = 0;
    public static long count =0;

    public static Map<String, Map<String, Object>> getChangeLog(String processId) {
        Set<ArrayValue> values = changeLog.get(processId);
        if(values == null) {
            return new HashMap<>();
        }
        Map<String, Map<String, Object>> log = new HashMap<>();
        for(ArrayValue value : values) {
            Set<String> connectedVariableIds = value.getConnectedArrayIds();
            Map<String, Object> loggedChange = new HashMap<>();
            for(ArrayValDataContainer indexChanged : value.getChangedIndexes()) {
                String key  = ArrayUtils.createArrayIndexList(Arrays.stream(indexChanged.getIndex()).boxed().collect(Collectors.toList()));
                loggedChange.put(key, indexChanged.get());
            }
            for(String connectedVarId : connectedVariableIds) {
                log.put(connectedVarId, loggedChange);
            }
        }
        return log;
    }

    public static void updateChangeLog(String processId, ArrayValue arrayValue) {
        Set<ArrayValue> changeLogForProcess = changeLog.get(processId);
        if(changeLogForProcess == null) {
            changeLogForProcess = new HashSet<>();
            changeLog.put(processId, changeLogForProcess);
        }
        changeLogForProcess.add(arrayValue);
    }

    public static void removeProcess(String processId) {
        Set<ArrayValue> set = changeLog.remove(processId);
        if(set != null) {
            set.clear();
        }
    }


}

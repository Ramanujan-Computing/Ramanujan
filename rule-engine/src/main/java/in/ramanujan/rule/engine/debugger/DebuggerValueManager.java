package in.ramanujan.rule.engine.debugger;

import in.ramanujan.debugger.UserReadableDebugPoint;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableRE;

import java.util.*;

public class DebuggerValueManager {
    private static Map<String, List<DebuggerPoint>> processDebugPoint = new HashMap<>();
    private static Map<String, IDebugPushClient> debugPushClientMap = new HashMap<>();

    private static Map<String, Map<String, RuleEngineInputUnit>> processRuleEngineUnitMap = new HashMap<>();

    private static Set<Integer> debugLineHooksSet = new HashSet<>();

    public static void addDebugPushClient(String processId, IDebugPushClient iDebugPushClient) {
        debugPushClientMap.put(processId, iDebugPushClient);
    }

    public static void addProcessRuleEngineUnitMap(String processId, Map<String, RuleEngineInputUnit> ruleEngineInputUnitMap) {
        processRuleEngineUnitMap.put(processId, ruleEngineInputUnitMap);
    }

    public static void addDebugLineHooksSet(Set<Integer> debugLineHooksSetInput) {
        debugLineHooksSet = debugLineHooksSetInput;
    }

    public static void removeProcess(String processId) {
        debugPushClientMap.remove(processId);
        processRuleEngineUnitMap.remove(processId);
    }

    public static void addDebugger(final String processId, final DebuggerPoint debuggerPoint) {
        if(!debugLineHooksSet.contains(debuggerPoint.getLine())) {
            return;
        }
        List<DebuggerPoint> pointsOnProcessor = processDebugPoint.get(processId);
        if(pointsOnProcessor == null) {
            pointsOnProcessor = new ArrayList<DebuggerPoint>();
            processDebugPoint.put(processId, pointsOnProcessor);
        }
        pointsOnProcessor.add(debuggerPoint);
        throw new DebugLineHooked();
    }

    public static List<UserReadableDebugPoint> getDebugPoints(final String processId, final Map<String, RuleEngineInputUnit> map) throws Exception {
        List<DebuggerPoint> debugPointList = processDebugPoint.get(processId);
        List<UserReadableDebugPoint> userReadableDebugPointList = new ArrayList<>();
        if(debugPointList == null) {
            return userReadableDebugPointList;
        }
        /*
        for(DebuggerPoint debuggerPoint : debugPointList) {
            UserReadableDebugPoint userReadableDebugPoint = new UserReadableDebugPoint();
            userReadableDebugPoint.setCommandId(debuggerPoint.getCommandId());
            userReadableDebugPointList.add(userReadableDebugPoint);
            final Map<Object, Object> beforeValues = debuggerPoint.getBeforeOp();
            final Map<Object, Object> afterValues = debuggerPoint.getAfterOp();
            //TODO: write logic to get readable values.

            for(Map.Entry<Object, Object> keyValPair : beforeValues.entrySet()) {
                Object key = keyValPair.getKey();
                String val = keyValPair.getValue() != null ? keyValPair.getValue().toString() : null;
                if(key instanceof VariableRE) {
                    VariableRE variableRE = (VariableRE) key;
                    userReadableDebugPoint.getBeforeValue().put(variableRE.getName(), val);
                    continue;
                }
                if(key instanceof ArrayContext) {
                    ArrayContext arrayContext = (ArrayContext) key;
                    String name = arrayContext.getArrayRE().getName() + "[" + arrayContext.getIndex() + "]";
                    userReadableDebugPoint.getBeforeValue().put(name, val);
                    continue;
                }
                throw new Exception(key + " is wrong type " + key.getClass().getName());
            }

            for(Map.Entry<Object, Object> keyValPair : afterValues.entrySet()) {
                Object key = keyValPair.getKey();
                String val = keyValPair.getValue() != null ? keyValPair.getValue().toString() : null;
                if(key instanceof VariableRE) {
                    VariableRE variableRE = (VariableRE) key;
                    userReadableDebugPoint.getAfterValue().put(variableRE.getName(), val);
                    continue;
                }
                if(key instanceof ArrayContext) {
                    ArrayContext arrayContext = (ArrayContext) key;
                    String name = arrayContext.getArrayRE().getName() + "[" + arrayContext.getIndex() + "]";
                    userReadableDebugPoint.getAfterValue().put(name, val);
                    continue;
                }
                throw new Exception(key + " is wrong type " + key.getClass().getName());
            }
            userReadableDebugPoint.setFunctionArgCallArgMap(debuggerPoint.getFunctionArgMap());
            userReadableDebugPoint.setConditionVal(debuggerPoint.getConditionVal());
            userReadableDebugPoint.setCodePtr(map.get(debuggerPoint.getCommandId()).getCodeStrPtr());
        }
         */
        return userReadableDebugPointList;
    }
}

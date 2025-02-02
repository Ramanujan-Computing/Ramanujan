package in.ramanujan.rule.engine.manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NextDagTriggerManager {
    private static Map<String, Map<String, Boolean>> map;
    public static Map<String, Boolean>getNextTriggerMap(String processId) {
        if(map == null) {
            map = new ConcurrentHashMap<>();
        }
        if(map.get(processId) != null) {
            return map.get(processId);
        } else {
            Map<String, Boolean> nextTriggerMap = new ConcurrentHashMap<>();
            map.put(processId, nextTriggerMap);
            return nextTriggerMap;
        }
    }

    public static void process(String processId, String nextDagId) {
        getNextTriggerMap(processId).put(nextDagId, true);
    }

}

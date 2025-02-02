package in.ramanujan.rule.engine.debugger;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class DebuggerPoint {
    @Getter
    @Setter
    String commandId;
    @Getter
    @Setter
    Integer line;
    Map<Object, Object> beforeOpValue = new HashMap<>(); // key to be either variableRE ot ArrayContext.
    Map<Object, Object> afterOpValue = new HashMap<>(); // key to be either variableRE ot ArrayContext.
    Map<String, String> functionArgCallArgMap = new HashMap<>();
    @Getter
    @Setter
    Boolean conditionVal;

    public void addFunctionArgCallMap(String key, String val) {
        functionArgCallArgMap.put(key, val);
    }

    public Map<String, String> getFunctionArgMap() {
        return functionArgCallArgMap;
    }

    public void addBeforeOp(Object key, Object value) {
        beforeOpValue.put(key, value);
    }

    public void addAfterOp(Object key, Object value) {
        afterOpValue.put(key, value);
    }

    public Map<Object, Object> getBeforeOp() {
        return beforeOpValue;
    }

    public Map<Object, Object> getAfterOp() {
        return afterOpValue;
    }

    public DebuggerPoint() {

    }
}

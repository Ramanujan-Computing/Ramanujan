package in.ramanujan.rule.engine.debugger;

import java.util.HashMap;
import java.util.Map;

public class NoDebugPoint extends DebuggerPoint {

    public static final NoDebugPoint INSTANCE = new NoDebugPoint();

    @Override
    public void addFunctionArgCallMap(String key, String val) {

    }

    @Override
    public Boolean getConditionVal() {
        return false;
    }

    @Override
    public void setConditionVal(Boolean conditionVal) {

    }

    @Override
    public Map<String, String> getFunctionArgMap() {
        return new HashMap<>();
    }

    private NoDebugPoint() {

    }


    @Override
    public Map<Object, Object> getBeforeOp() {
        return new HashMap<>();
    }

    @Override
    public Map<Object, Object> getAfterOp() {
        return new HashMap<>();
    }

    @Override
    public void addBeforeOp(Object key, Object value) {
    }

    @Override
    public void addAfterOp(Object key, Object value) {
    }
}

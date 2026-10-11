package in.ramanujan.middleware.base.pojo;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DagVertexOutput implements Serializable {
    private String asyncId;
    private String dagElementId;
    private Map<String, Object> variableMap = new HashMap<>();
    private Map<String, Map<String, Object>> arrayMap = new HashMap<>(); // arrayId -> (indexStr -> value)

    public DagVertexOutput() {}

    public DagVertexOutput(String asyncId, String dagElementId) {
        this.asyncId = asyncId;
        this.dagElementId = dagElementId;
    }

    public String getAsyncId() {
        return asyncId;
    }

    public void setAsyncId(String asyncId) {
        this.asyncId = asyncId;
    }

    public String getDagElementId() {
        return dagElementId;
    }

    public void setDagElementId(String dagElementId) {
        this.dagElementId = dagElementId;
    }

    public Map<String, Object> getVariableMap() {
        return variableMap;
    }

    public void setVariableMap(Map<String, Object> variableMap) {
        this.variableMap = variableMap;
    }

    public Map<String, Map<String, Object>> getArrayMap() {
        return arrayMap;
    }

    public void setArrayMap(Map<String, Map<String, Object>> arrayMap) {
        this.arrayMap = arrayMap;
    }
}

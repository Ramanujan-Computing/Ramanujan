package in.ramanujan.middleware.base.pojo;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CompactedDagInput implements Serializable {
    private String asyncId;
    private String dagElementId;
    private Map<String, Object> coalescedVariables = new HashMap<>();
    private Map<String, Map<String, Object>> coalescedArrays = new HashMap<>(); // arrayId -> (sorted indexStr -> value)

    public CompactedDagInput() {}

    public CompactedDagInput(String asyncId, String dagElementId) {
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

    public Map<String, Object> getCoalescedVariables() {
        return coalescedVariables;
    }

    public void setCoalescedVariables(Map<String, Object> coalescedVariables) {
        this.coalescedVariables = coalescedVariables;
    }

    public Map<String, Map<String, Object>> getCoalescedArrays() {
        return coalescedArrays;
    }

    public void setCoalescedArrays(Map<String, Map<String, Object>> coalescedArrays) {
        this.coalescedArrays = coalescedArrays;
    }
}

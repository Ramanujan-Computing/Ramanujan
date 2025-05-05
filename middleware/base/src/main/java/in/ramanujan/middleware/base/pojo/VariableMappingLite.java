package in.ramanujan.middleware.base.pojo;

import java.io.Serializable;

public class VariableMappingLite implements Serializable {
    private String variableId;
    private String variableName;
    private String asyncId;
    private Object object;

    public VariableMappingLite() {}

    public VariableMappingLite(String variableId, String variableName, String asyncId, Object object) {
        this.variableId = variableId;
        this.variableName = variableName;
        this.asyncId = asyncId;
        this.object = object;
    }

    public String getVariableId() { return variableId; }
    public void setVariableId(String variableId) { this.variableId = variableId; }

    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }

    public String getAsyncId() { return asyncId; }
    public void setAsyncId(String asyncId) { this.asyncId = asyncId; }

    public Object getObject() { return object; }
    public void setObject(Object object) { this.object = object; }
}

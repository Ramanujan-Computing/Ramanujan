package in.ramanujan.middleware.base.pojo;

import java.io.Serializable;

public class ArrayMappingLite implements Serializable {
    private String arrayId;
    private String arrayName;
    private String asyncId;
    private String indexStr;
    private Object object;

    public ArrayMappingLite() {}

    public ArrayMappingLite(String arrayId, String arrayName, String asyncId, String indexStr, Object object) {
        this.arrayId = arrayId;
        this.arrayName = arrayName;
        this.asyncId = asyncId;
        this.indexStr = indexStr;
        this.object = object;
    }

    public String getArrayId() { return arrayId; }
    public void setArrayId(String arrayId) { this.arrayId = arrayId; }

    public String getArrayName() { return arrayName; }
    public void setArrayName(String arrayName) { this.arrayName = arrayName; }

    public String getAsyncId() { return asyncId; }
    public void setAsyncId(String asyncId) { this.asyncId = asyncId; }

    public String getIndexStr() { return indexStr; }
    public void setIndexStr(String indexStr) { this.indexStr = indexStr; }

    public Object getObject() { return object; }
    public void setObject(Object object) { this.object = object; }
}

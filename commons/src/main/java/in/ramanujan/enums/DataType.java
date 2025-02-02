package in.ramanujan.enums;

public enum  DataType {
    Integer("Integer"),
    String("String"),
    Double("Double");

    private String dataTypeName;

    DataType(String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }

    public java.lang.String getDataTypeName() {
        return dataTypeName;
    }

    public void setDataTypeName(java.lang.String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }

    public static DataType getDataTypeInfo(String dataType) {
        for(DataType dataType1 : values()) {
            if(dataType1.getDataTypeName().equalsIgnoreCase(dataType)) {
                return dataType1;
            }
        }
        return null;
    }
}

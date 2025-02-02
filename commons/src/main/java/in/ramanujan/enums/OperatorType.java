package in.ramanujan.enums;

public enum OperatorType {
    ADD("add", "+"),
    ASSIGN("assign", "="),
    MINUS("minus", "-"),
    MULTIPLY("multiply", "*"),
    DIVIDE("divide", "/"),
    POWER("power", "^"),
    LOG("log", "log"),
    SINE("sine", "sine"),
    COSINE("cosine", "cosine");

    private String operatorType;
    private String operatorCode;

    OperatorType(String operatorType, String operatorCode) {
        this.operatorType = operatorType;
        this.operatorCode = operatorCode;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }

    public static OperatorType getOperatorTypeInfo(String operatorType) {
        for(OperatorType operatorType1 : values()) {
            if(operatorType1.getOperatorCode().equalsIgnoreCase(operatorType)) {
                return operatorType1;
            }
        }
        return null;
    }

    public String getOperatorCode() {
        return operatorCode;
    }

    public void setOperatorCode(String operatorCode) {
        this.operatorCode = operatorCode;
    }
}

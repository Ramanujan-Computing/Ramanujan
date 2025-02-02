package in.ramanujan.enums;

public enum ConditionType {
    lessThan("<"),
    and("&&"),
    or("||"),
    isEqual("=="),
    isNotEqual("!="),
    lessThanEqualTo("<="),
    greaterThan(">"),
    greaterThanEqualTo(">="),
    not("not");

    private String conditionTypeString;

    ConditionType(String conditionTypeString) {
        this.conditionTypeString = conditionTypeString;
    }

    public String getConditionTypeString() {
        return conditionTypeString;
    }

    public void setConditionTypeString(String conditionTypeString) {
        this.conditionTypeString = conditionTypeString;
    }

    public static ConditionType getConditionType(String conditionTypeString) {
        for(ConditionType conditionType : values()) {
            if(conditionType.getConditionTypeString().equalsIgnoreCase(conditionTypeString)) {
                return  conditionType;
            }
        }
        return null;
    }
}

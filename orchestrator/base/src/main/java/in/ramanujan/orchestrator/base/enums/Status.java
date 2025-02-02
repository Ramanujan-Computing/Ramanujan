package in.ramanujan.orchestrator.base.enums;

public enum  Status {
    SUCCESS("SUCCESS"),
    PROCESSING("PROCESSING"),
    FAILURE("FAILURE"),
    CHECKPOINT("CHECKPOINT");

    private String keyName;

    Status(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyName() {
        return  keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
}

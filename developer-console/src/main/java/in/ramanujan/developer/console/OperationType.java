package in.ramanujan.developer.console;

import in.ramanujan.developer.console.operationImpl.*;

public enum OperationType {
    execute("execute", new ExecutorImpl()),
    build("build", new Builder()),
    execute_all("executePackage", new ExecutePackageImpl()),
    suspend("suspend", new SuspendImpl()),
    debug("debug", new DebugFetcher()),
    execute_inline("execute_inline", new ExecuteInline());

    String type;
    Operation implementation;

    OperationType(String type, Operation implementation) {
        this.type = type;
        this.implementation = implementation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Operation getImplementation() {
        return implementation;
    }

    public void setImplementation(Operation implementation) {
        this.implementation = implementation;
    }

    public static OperationType getOperation(String type) {
        for(OperationType operationType : values()) {
            if(operationType.getType().equalsIgnoreCase(type)) {
                return operationType;
            }
        }
        // Default to execute_inline
        return execute_inline;
    }
}

package in.ramanujan.db.layer.enums;

public enum QueryType {
    INSERT,
    UPDATE,
    DELETE,
    UPSERT,
    SELECT,
    SELECT_IN;  // Added for IN clause batch operations
}

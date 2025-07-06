package in.ramanujan.db.layer.enums;

public enum StorageType {
    LOCAL, GCP;

    public static StorageType fromString(String type) {
        for(StorageType storageType : StorageType.values()) {
            if(storageType.name().equalsIgnoreCase(type)) {
                return storageType;
            }
        }
        return null;
    }
}

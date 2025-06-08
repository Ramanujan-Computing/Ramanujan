package in.ramanujan.orchestrator.base.configuration;

import in.ramanujan.db.layer.enums.StorageType;
import in.ramanujan.db.layer.utils.QueryExecutor;
import io.vertx.core.json.JsonObject;

import static in.ramanujan.orchestrator.base.configuration.ConfigKey.DB_TYPE_CONFIG_KEY;

public class ConfigurationGetter {
    private static JsonObject config;

    public static void init(JsonObject jsonObject) {
        config = jsonObject;
    }

    public static String getString(String key) {
        return config.getString(key);
    }

    public static Integer getInt(String key) {
        return config.getInteger(key);
    }

    public static QueryExecutor.DB_TYPE getDBType() {
        return QueryExecutor.DB_TYPE.getDBType(config.getString(DB_TYPE_CONFIG_KEY));
    }

    public static in.ramanujan.monitoringutils.verticles.MonitoringVerticle.MONITORING_TYPE getMonitoringType() {
        return in.ramanujan.monitoringutils.verticles.MonitoringVerticle.MONITORING_TYPE.fromString(config.getString(ConfigKey.MONITORING_TYPE));
    }

    public static StorageType getStorageType() {
        return StorageType.fromString(config.getString(ConfigKey.STORAGE_TYPE));
    }
}

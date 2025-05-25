package in.ramanujan.middleware.base.configuration;

public class ConfigKey {
    public static final String ORCHESTRATOR_HOST_KEY = "orchestrator.host";
    public static final String ORCHESTRATOR_PORT_KEY = "orchestrator.port";

    public static final String KAFKA_MANAGER_HOST_KEY = "kafka.manager.host";
    public static final String KAFKA_MANAGER_PORT_KEY = "kafka.manager.port";

    public static final String DB_TYPE_CONFIG_KEY = "db.type";
    public static final String STORAGE_TYPE = "storage.type";
    public static final String MONITORING_TYPE = "monitoring.type";

    // Path to the GCS credentials file for middleware
    public static final String MIDDLEWARE_GCS_CREDENTIALS_PATH = "middleware.gcs.credentials.path";
    // Path to the MetricPusher credentials file for middleware
    public static final String METRIC_PUSHER_CRED_PATH = "middleware.metric.pusher.cred.path";
}

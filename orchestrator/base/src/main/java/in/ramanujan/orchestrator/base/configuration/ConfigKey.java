package in.ramanujan.orchestrator.base.configuration;

public class ConfigKey {
    public static final String ORCHESTRATOR_HOST_KEY = "orchestrator.host";
    public static final String ORCHESTRATOR_PORT_KEY = "orchestrator.port";
    public static final String ENV = "env";

    public static final String DB_TYPE_CONFIG_KEY = "db.type";
    public static final String STORAGE_TYPE = "storage.type";
    public static final String MONITORING_TYPE = "monitoring.type";

    // Path to the GCS credentials file for orchestrator
    public static final String ORCHESTRATOR_GCS_CREDENTIALS_PATH = "orchestrator.gcs.credentials.path";
    // Path to the MetricPusher credentials file for orchestrator
    public static final String METRIC_PUSHER_CRED_PATH = "orchestrator.metric.pusher.cred.path";
}

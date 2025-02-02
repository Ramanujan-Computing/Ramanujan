package in.robinhood.ramanujan.orchestrator.base.configuration;

import io.vertx.core.json.JsonObject;

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
}

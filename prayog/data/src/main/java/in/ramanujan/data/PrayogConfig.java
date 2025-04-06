package in.ramanujan.data;

import io.vertx.core.json.JsonObject;

public class PrayogConfig {
    private PrayogConfig() {

    }

    private static JsonObject CONFIG;

    public static void CONFIG(JsonObject config) {
        CONFIG = config;
    }

    public static String getString(String key) {
        return CONFIG.getString(key);
    }
}

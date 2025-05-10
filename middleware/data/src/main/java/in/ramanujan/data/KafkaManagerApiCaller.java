package in.ramanujan.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.middleware.base.configuration.ConfigKey;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class KafkaManagerApiCaller {
    private WebClient webClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger = LoggerFactory.getLogger(KafkaManagerApiCaller.class);

    public Vertx vertx;

    private WebClient getWebClient() {
        if(webClient == null) {
            webClient = WebClient.create(
                    vertx,
                    new WebClientOptions()
                            .setDefaultHost(ConfigurationGetter.getString(ConfigKey.KAFKA_MANAGER_HOST_KEY))
                            .setDefaultPort(ConfigurationGetter.getInt(ConfigKey.KAFKA_MANAGER_PORT_KEY))
                            .setMaxPoolSize(100)
            );
        }
        return webClient;
    }

    public void setWebClient(String ip, int port) {
        webClient = WebClient.create(
                vertx,
                new WebClientOptions()
                        .setDefaultHost(ip)
                        .setDefaultPort(port)
                        .setMaxPoolSize(100)
        );
    }

    public Future<Void> callEventApi(String asyncId, String dagElementId, Boolean toBeDebugged) {
        Future<Void> future = Future.future();
        final JsonObject jsonObject = new JsonObject()
                .put("asyncId", asyncId)
                .put("dagElementId", dagElementId)
                .put("toBeDebugged", toBeDebugged);
        logger.info("CALLING KAFKA-MGR: " + dagElementId);
        getWebClient().post("/handle").sendJsonObject(jsonObject, new MonitoringHandler<>("pubSubEventAPI", handler -> {
            if(handler.succeeded()) {
                future.complete();
            } else {
                callEventApi(asyncId, dagElementId, toBeDebugged).setHandler(reHandler -> {
                    future.complete();
                });
            }
        }));
        return future;
    }

}

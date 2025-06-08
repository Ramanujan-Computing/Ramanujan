package in.ramanujan.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.middleware.base.configuration.ConfigKey;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import in.ramanujan.service.EventProducer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KafkaManagerApiCaller {
    private WebClient webClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger = LoggerFactory.getLogger(KafkaManagerApiCaller.class);

    public Vertx vertx;

    @Autowired
    private EventProducer eventProducer;

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
        CheckStatusQueueEvent checkStatusQueueEvent = new CheckStatusQueueEvent();
        checkStatusQueueEvent.setAsyncId(asyncId);
        checkStatusQueueEvent.setDagElementId(dagElementId);
        checkStatusQueueEvent.setToBeDebugged(toBeDebugged);

        logger.info("CALLING KAFKA-MGR: " + dagElementId);
        eventProducer.produce(checkStatusQueueEvent).setHandler(handler -> {
            if(handler.succeeded()) {
                future.complete();
            } else {
                logger.error("Failed to produce event to Kafka Manager", handler.cause());
                future.fail(handler.cause());
            }
        });
        return future;
    }

}

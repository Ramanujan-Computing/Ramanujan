package in.ramanujan.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.base.configuration.ConfigKey;
import in.ramanujan.base.configuration.ConfigurationGetter;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MiddlewareClient {
    private WebClient webClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger = LoggerFactory.getLogger(MiddlewareClient.class);


    private ConsumptionCallback consumptionCallback; // to be inited by middleware.

    public void setConsumptionCallback(ConsumptionCallback consumptionCallback) {
        this.consumptionCallback = consumptionCallback;
    }


    private WebClient getWebClient() {
        if(webClient == null) {
            webClient = WebClient.create(
                    Vertx.vertx(),
                    new WebClientOptions()
                            .setDefaultHost(ConfigurationGetter.getString(ConfigKey.MIDDLEWARE_HOST_KEY))
                            .setDefaultPort(ConfigurationGetter.getInt(ConfigKey.MIDDLEWARE_PORT_KEY))
            );
        }
        return webClient;
    }

    public void setWebClient(String ip, int port) {
        webClient = WebClient.create(
                Vertx.vertx(),
                new WebClientOptions()
                        .setDefaultHost(ip)
                        .setDefaultPort(port)
        );
    }

    public Future<Void> callMiddlewareProcessNextElementApi(String asyncId, String dagElementId, Boolean toBeDebugged, Vertx vertx) {
        Future future = Future.future();
        vertx.executeBlocking(
                blockingHandler -> {
                    try {
                        logger.info("Processing next element for asyncId: {}, dagElementId: {}, toBeDebugged: {}", asyncId, dagElementId, toBeDebugged);
                        consumptionCallback.processNextElement(asyncId, dagElementId, toBeDebugged, vertx).setHandler(handler -> {
                            logger.info("Processed next element for asyncId: {}, dagElementId: {}, toBeDebugged: {}", asyncId, dagElementId, toBeDebugged);
                            blockingHandler.complete();
                        });
                    } catch (Exception e) {
                        logger.error("Error processing next element", e);
                        blockingHandler.fail(e);
                    }
                },
                false, handler -> {
                    if(handler.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(handler.cause());
                    }
                }
        );

        return future;
    }

    public static interface ConsumptionCallback {
        Future<Void> processNextElement(String asyncId, String dagElementId, Boolean toBeDebugged, Vertx vertx) throws Exception;
    }

}

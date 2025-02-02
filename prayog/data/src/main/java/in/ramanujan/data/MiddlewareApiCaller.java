package in.ramanujan.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MiddlewareApiCaller {
    private Logger logger = LoggerFactory.getLogger(MiddlewareApiCaller.class);
    private WebClient webClient;
    private String host = "localhost";
    private int port = 8888;
    private String runUri = "/run";
    private ObjectMapper objectMapper = new ObjectMapper();


    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.create(
                    Vertx.vertx(),
                    new WebClientOptions()
                            .setDefaultHost(host)
                            .setDefaultPort(port)
            );
        }
        return webClient;
    }
    public Future<Void> run(JsonObject jsonObject, Long initTime) {
        Future<Void> future = Future.future();
        getWebClient().post(runUri).sendJsonObject(jsonObject, handler -> {
            if(handler.succeeded() && handler.result().statusCode() == 200) {
                logger.info(new Date().toInstant().toEpochMilli() - initTime);
                future.complete();
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }
}

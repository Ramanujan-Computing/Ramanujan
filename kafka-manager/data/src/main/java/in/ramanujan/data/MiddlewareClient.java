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
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MiddlewareClient {
    private WebClient webClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger = LoggerFactory.getLogger(MiddlewareClient.class);


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

    public Future<Void> callMiddlewareProcessNextElementApi(String asyncId, String dagElementId, Boolean toBeDebugged) {
        Future<Void> future = Future.future();
        JsonObject jsonObject = new JsonObject()
                .put("asyncId", asyncId)
                .put("dagElementId", dagElementId)
                .put("toBeDebugged", toBeDebugged)
                .put("source", "kafka");
        Long startDateTime = new Date().toInstant().toEpochMilli();
        getWebClient().put("/process/next").sendJsonObject(jsonObject, httpResponseAsyncResult -> {
            logger.info("latency: " + (new Date().toInstant().toEpochMilli() - startDateTime));
            if(httpResponseAsyncResult.succeeded()) {
                if(httpResponseAsyncResult.result().statusCode() == 200) {
                    future.complete();
                } else {
                    logger.error("API failed with status " + httpResponseAsyncResult.result().statusCode());
                    future.fail("API status not 200");
                }
            } else {
                future.fail(httpResponseAsyncResult.cause());
            }
//            if(httpResponseAsyncResult.succeeded()) {
//                future.complete();
//            } else {
//                callMiddlewareProcessNextElementApi(asyncId, dagElementId).setHandler(rehandler -> {
//                    future.complete();
//                });
//            }
        });
        return future;
    }

}

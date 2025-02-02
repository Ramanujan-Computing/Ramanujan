package in.robinhood.ramanujan.orchestrator.data.external;

import in.robinhood.ramanujan.orchestrator.base.configuration.ConfigKey;
import in.robinhood.ramanujan.orchestrator.base.configuration.ConfigurationGetter;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.springframework.stereotype.Component;

@Component
public class OrchestratorApiCaller {

    private WebClient webClient;

    private String openPingUri = "/pings/open";

    private WebClient getWebClient() {
        if(webClient == null) {
            webClient = WebClient.create(
                    Vertx.vertx(),
                    new WebClientOptions()
                            .setDefaultHost(ConfigurationGetter.getString(ConfigKey.ORCHESTRATOR_HOST_KEY))
                            .setDefaultPort(ConfigurationGetter.getInt(ConfigKey.ORCHESTRATOR_PORT_KEY))
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

    public Future<Void> callOpenPingApiWithRetry(final String hostId, final Integer retryNumber) {
        Future<Void> future = Future.future();
        callOpenPingApi(hostId).setHandler(handler -> {
            if(handler.succeeded()){
                future.complete();
            } else {
                if(retryNumber == 0) {
                    future.fail(handler.cause());
                } else {
                    callOpenPingApiWithRetry(hostId, retryNumber - 1).setHandler(retryHandler -> {
                       if(retryHandler.succeeded()) {
                           future.complete();
                       } else {
                           future.fail(retryHandler.cause());
                       }
                    });
                }
            }
        });
        return future;
    }

    private Future<Void> callOpenPingApi(final String hostId) {
        Future<Void> future = Future.future();
        String queryParam = "?uuid=" + hostId;
        getWebClient().post(openPingUri + queryParam).sendJsonObject(new JsonObject(), handler -> {
            if(handler.succeeded()) {
                future.complete();
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }
}

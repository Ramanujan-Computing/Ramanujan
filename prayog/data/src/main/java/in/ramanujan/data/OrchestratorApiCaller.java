package in.ramanujan.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.rule.engine.Processor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OrchestratorApiCaller {

    private String host = "localhost";
    private int port = 8890;
    private String pingUri = "/pings/open";
    private String heartBeatUri = "/pings/heartbeat";
    private String completionUri = "/task/complete";
    private WebClient webClient;

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

    public Future<Void> sendHeartBeat(String uuid, Map<String, Processor> processorHostMap) {
        getWebClient().post(heartBeatUri).addQueryParam("uuid", uuid).send(handler ->{
            if(handler.succeeded()) {
                if(handler.result().statusCode() == 200) {
                    Boolean toContinueProcess = handler.result().bodyAsJsonObject().getBoolean("data");
                    if(!toContinueProcess) {
                        processorHostMap.get(uuid).endProcess();
                    }
                }
            }
        });
        return Future.succeededFuture();
    }

    public Future<Map<String, RuleEngineInput>> getProcessingInput(String uuid) {
        Future<Map<String, RuleEngineInput>> future = Future.future();
        getWebClient().post(pingUri).addQueryParam("uuid", uuid).send(handler -> {
            if (handler.succeeded()) {
                JsonObject result = new JsonObject();
                try {
                    result = handler.result().bodyAsJsonObject();
                } catch (Exception e) {
                    int a= 1;
                    a++;
                }
                if (result.getJsonObject("data") == null) {
                    future.complete();
                } else {
                    try {
                        String firstCommandId = result.getJsonObject("data").getString("firstCommandId");
                        RuleEngineInput ruleEngineInput = objectMapper.readValue(result.getJsonObject("data")
                                .getJsonObject("ruleEngineInput").toString(), RuleEngineInput.class);
                        Map<String, RuleEngineInput> processingMap = new HashMap<>();
                        processingMap.put(firstCommandId, ruleEngineInput);
                        future.complete(processingMap);
                    } catch (Exception e) {
                        future.fail(e);
                    }
                }
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    public Future<Void> submitProcessingInputWithRetries(String uuid, Map<String, Object> result) {
        Future<Void> future = Future.future();
        submitProcessingInput(uuid, result).setHandler(handler -> {
            if (handler.succeeded()) {
                future.complete();
            } else {
                submitProcessingInputWithRetries(uuid, result).setHandler(retry -> {
                    if (retry.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(retry.cause());
                    }
                });
            }
        });
        return future;
    }

    private Future<Void> submitProcessingInput(String uuid, Map<String, Object> result) {
        Future future = Future.future();
        JsonObject jsonObject = new JsonObject()
                .put("hostId", uuid)
                .put("data", result);
        getWebClient().post(completionUri).sendJsonObject(jsonObject, handler -> {
            if (handler.succeeded()) {
                future.complete();
                ;
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }
}

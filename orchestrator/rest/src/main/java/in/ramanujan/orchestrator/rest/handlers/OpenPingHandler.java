package in.ramanujan.orchestrator.rest.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.ramanujan.orchestrator.service.OpenPingService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenPingHandler implements Handler<RoutingContext> {

    @Autowired
    private OpenPingService openPingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(RoutingContext event) {
        String hostId = event.queryParams().get("uuid");
        openPingService.storePing(hostId).setHandler(new MonitoringHandler<>("openPing", handler -> {
            if(handler.succeeded()) {
                ApiResponse apiResponse = new ApiResponse(Status.SUCCESS.getKeyName(), handler.result());
                try {
                    String responseStr = objectMapper.writeValueAsString(apiResponse);
                    event.response().setStatusCode(HttpResponseStatus.OK.code()).end(responseStr);
                } catch (Exception e) {
                    apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), e);
                    event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                            JsonObject.mapFrom(apiResponse).toString());
                }
            } else {
                ApiResponse apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), handler.cause());
                event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                        JsonObject.mapFrom(apiResponse).toString()
                );
            }
        }));
    }
}

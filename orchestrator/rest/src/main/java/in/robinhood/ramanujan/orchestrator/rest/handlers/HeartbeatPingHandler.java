package in.robinhood.ramanujan.orchestrator.rest.handlers;

import in.ramanujan.monitoringutils.MonitoringHandler;
import in.robinhood.ramanujan.orchestrator.base.enums.Status;
import in.robinhood.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.robinhood.ramanujan.orchestrator.service.HeartbeatPingService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatPingHandler implements Handler<RoutingContext> {

    @Autowired
    private HeartbeatPingService heartbeatPingService;

    private final Logger logger = LoggerFactory.getLogger(HeartbeatPingHandler.class);

    @Override
    public void handle(RoutingContext event) {
        String hostId = event.queryParams().get("uuid");
        logger.info("Received hearbeat for " + hostId);
        heartbeatPingService.pingHeartbeat(hostId).setHandler(new MonitoringHandler<>("heartbeat", handler -> {
            if(handler.succeeded()) {
                ApiResponse apiResponse = new ApiResponse(Status.SUCCESS.getKeyName(), handler.result());
                event.response().setStatusCode(HttpResponseStatus.OK.code()).end(JsonObject.mapFrom(apiResponse).toString());
            } else {
                logger.error("Heartbeat update failed", handler.cause());
                event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
            }
        }));
    }
}

package in.ramanujan.orchestrator.rest.handlers;

import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.ramanujan.orchestrator.service.TaskCompleteService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskCompleteHandler implements Handler<RoutingContext> {

    @Autowired
    private TaskCompleteService taskCompleteService;

    private final Logger logger = LoggerFactory.getLogger(TaskCompleteHandler.class);

    @Override
    public void handle(RoutingContext event) {
        JsonObject body = event.getBodyAsJson();
        String hostId = body.getString("hostId");
        String uuid = body.getString("uuid");
        Object data = body.getJsonObject("data").getMap();

        taskCompleteService.completeTask(hostId, uuid, data).setHandler(new MonitoringHandler<>("taskCompletion", handler -> {
            if(handler.succeeded()) {
                ApiResponse apiResponse = new ApiResponse(Status.SUCCESS.getKeyName(), null);
                event.response().setStatusCode(HttpResponseStatus.OK.code()).end(JsonObject.mapFrom(apiResponse).toString());
            } else {
                logger.error("Failed to complete task for hostId " +  hostId, handler.cause());
                ApiResponse apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), handler.cause());
                event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                        JsonObject.mapFrom(apiResponse).toString()
                );
            }
        }));
    }

    private Object getDebugDataFromRequest(JsonObject body) {
        JsonObject obj = body.getJsonObject("debugData");
        if(obj == null) {
            return null;
        }
        return obj.getMap();
    }
}

package in.ramanujan.orchestrator.rest.handlers;

import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.ramanujan.orchestrator.service.TaskCompleteService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Future;
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
        JsonObject body;
        Future<Void> completion;
        String hostId;
        try {
            body = event.getBodyAsJson();
            if (body == null || body.getString("hostId") == null || body.getString("uuid") == null) {
                throw new IllegalArgumentException("Task and worker identifiers are required");
            }
            hostId = body.getString("hostId");
            String uuid = body.getString("uuid");
            JsonObject data = body.getJsonObject("data");
            String objectName = body.getString("resultObject");
            if (objectName != null && data == null) {
                Long size = body.getLong("resultSize");
                String md5 = body.getString("resultMd5");
                if (size == null || size <= 0 || md5 == null || md5.isEmpty()) {
                    throw new IllegalArgumentException("Uploaded result size and checksum are required");
                }
                completion = taskCompleteService.completeUploadedTask(hostId, uuid, objectName, size, md5);
            } else if (data != null && objectName == null) {
                completion = taskCompleteService.completeTask(hostId, uuid, data.getMap());
            } else {
                throw new IllegalArgumentException("Specify either inline data or an uploaded result reference");
            }
        } catch (IllegalArgumentException | ClassCastException | io.vertx.core.json.DecodeException e) {
            logger.warn("Invalid task completion request", e);
            event.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end(new JsonObject().put("status", Status.FAILURE.getKeyName())
                            .put("message", e.getMessage()).encode());
            return;
        }

        completion.setHandler(new MonitoringHandler<>("taskCompletion", handler -> {
            if(handler.succeeded()) {
                ApiResponse apiResponse = new ApiResponse(Status.SUCCESS.getKeyName(), null);
                event.response().setStatusCode(HttpResponseStatus.OK.code()).end(JsonObject.mapFrom(apiResponse).toString());
            } else {
                logger.error("Failed to complete task for hostId " +  hostId, handler.cause());
                ApiResponse apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), handler.cause().getMessage());
                HttpResponseStatus status = handler.cause() instanceof IllegalArgumentException
                        ? HttpResponseStatus.BAD_REQUEST
                        : handler.cause() instanceof IllegalStateException
                        ? HttpResponseStatus.CONFLICT : HttpResponseStatus.INTERNAL_SERVER_ERROR;
                event.response().setStatusCode(status.code()).end(
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

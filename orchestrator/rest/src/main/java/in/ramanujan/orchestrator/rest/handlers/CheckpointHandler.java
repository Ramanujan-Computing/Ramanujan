package in.ramanujan.orchestrator.rest.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.ramanujan.orchestrator.service.CheckpointService;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckpointHandler implements Handler<RoutingContext> {

    private Logger logger = LoggerFactory.getLogger(CheckpointHandler.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CheckpointService checkpointService;

    @Override
    public void handle(RoutingContext event) {
        JsonObject payload = event.getBodyAsJson();
        String asyncTaskId = payload.getString("uuid");
        try {
            Checkpoint checkpoint = objectMapper.readValue(payload.getJsonObject("checkpoint").toString(), Checkpoint.class);
            checkpointService.applyCheckpoint(checkpoint, asyncTaskId)
                    .setHandler(new MonitoringHandler<>("checkpoint",handler -> {
                if(handler.succeeded()) {
                    event.response().setStatusCode(HttpResponseStatus.OK.code()).end(
                            JsonObject.mapFrom(new ApiResponse(
                                    Status.SUCCESS.getKeyName(), null
                            )).toString()
                    );
                } else {
                    event.response().setStatusCode(HttpResponseStatus.OK.code()).end(
                            JsonObject.mapFrom(new ApiResponse(
                                    Status.FAILURE.getKeyName(), handler.cause()
                            )).toString()
                    );
                }
            }));
        } catch (Exception e) {
            event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                    JsonObject.mapFrom(new ApiResponse(Status.FAILURE.getKeyName(), e)).toString()
            );
        }
    }
}

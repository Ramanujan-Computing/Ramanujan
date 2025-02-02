package in.robinhood.ramanujan.orchestrator.rest.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.robinhood.ramanujan.orchestrator.base.enums.Status;
import in.robinhood.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.robinhood.ramanujan.orchestrator.base.pojo.CheckpointResumePayload;
import in.robinhood.ramanujan.orchestrator.service.CheckpointResumeService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CheckpointResumeHandler implements Handler<RoutingContext> {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CheckpointResumeService checkpointResumeService;

    @Override
    public void handle(RoutingContext event) {
        MultiMap map = event.queryParams();
        String asyncId = map.get("asyncId");
        CheckpointResumePayload checkpointResumePayload = new CheckpointResumePayload();
        try {
            checkpointResumePayload = objectMapper.readValue(event.getBodyAsString(), CheckpointResumePayload.class);
        } catch (IOException ignored) {}
        checkpointResumeService.resumeCheckpoint(asyncId, checkpointResumePayload).setHandler(handler -> {
           if(handler.succeeded()) {
               event.response().setStatusCode(HttpResponseStatus.OK.code()).end(
                       JsonObject.mapFrom(new ApiResponse(
                               Status.SUCCESS.getKeyName(), null
                       )).toString());
           } else {
               event.response().setStatusCode(HttpResponseStatus.OK.code()).end(
                       JsonObject.mapFrom(new ApiResponse(
                               Status.FAILURE.getKeyName(), handler.cause()
                       )).toString());
           }
        });
    }
}

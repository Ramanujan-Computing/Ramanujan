package in.ramanujan.middleware.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.middleware.base.pojo.ApiResponse;
import in.ramanujan.middleware.base.pojo.CheckpointResumePayload;
import in.ramanujan.middleware.service.CheckpointResumeService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;

public class CheckpointResumeHandler extends CommonHandler {

    public CheckpointResumeService checkpointResumeService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        MultiMap queryMap = routingContext.queryParams();

        final String asyncId = queryMap.get("asyncId");
        final String dagElementId = queryMap.get("dagElementId");
        CheckpointResumePayload payload = new CheckpointResumePayload();
        try {
            payload = objectMapper.readValue(body.toString(), CheckpointResumePayload.class);
        } catch (IOException ignored) {

        }
        checkpointResumeService.resumeCheckpoint(asyncId, dagElementId, payload).setHandler(handler -> {
           if(handler.succeeded()) {
               respondSuccess(routingContext, new ApiResponse(HttpResponseStatus.OK.toString(), null));
           } else {
               respondInternalServerError(routingContext, new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(), handler.cause()));
           }
        });
    }
}

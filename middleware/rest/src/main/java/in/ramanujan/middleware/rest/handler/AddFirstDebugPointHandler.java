package in.ramanujan.middleware.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.model.pojo.FirstDebugPointPayload;
import in.ramanujan.middleware.base.pojo.ApiResponse;
import in.ramanujan.middleware.service.RunService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AddFirstDebugPointHandler extends CommonHandler {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RunService runService;

    @Override
    public void handle(RoutingContext routingContext) {
        FirstDebugPointPayload payload = new FirstDebugPointPayload();
        String asyncId = routingContext.queryParams().get("asyncId");
        String firstDagElementId = routingContext.queryParams().get("dagElementId");
        try {
            payload = objectMapper.readValue(routingContext.getBodyAsString(), FirstDebugPointPayload.class);
        } catch (IOException e) {
        }
        runService.addDebugPoints(asyncId, firstDagElementId, payload, routingContext.vertx()).setHandler(handler -> {
           if(handler.succeeded()) {
               ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.OK.toString(), "");
               respondSuccess(routingContext, apiResponse);
           } else {
               ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(), "");
               respondInternalServerError(routingContext, apiResponse);
           }
        });
    }
}

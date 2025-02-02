package in.robinhood.ramanujan.orchestrator.rest.handlers;

import in.robinhood.ramanujan.orchestrator.base.enums.Status;
import in.robinhood.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.robinhood.ramanujan.orchestrator.service.DebugInfoPushService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DebugInfoPushHandler implements Handler<RoutingContext> {

    @Autowired
    private DebugInfoPushService debugInfoPushService;


    @Override
    public void handle(RoutingContext routingContext) {
        String body = routingContext.getBodyAsString();
        String asyncId = routingContext.queryParams().get("asyncId");

        debugInfoPushService.push(asyncId, body).setHandler(handler -> {
           if(handler.succeeded()) {
               ApiResponse apiResponse = new ApiResponse(Status.SUCCESS.getKeyName(), null);
               routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(JsonObject.mapFrom(apiResponse).toString());
           } else {
               ApiResponse apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), handler.cause());
               routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                       JsonObject.mapFrom(apiResponse).toString()
               );
           }
        });
    }
}

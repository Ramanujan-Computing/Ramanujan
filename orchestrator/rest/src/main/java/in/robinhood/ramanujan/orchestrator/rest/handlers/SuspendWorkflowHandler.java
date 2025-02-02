package in.robinhood.ramanujan.orchestrator.rest.handlers;

import in.robinhood.ramanujan.orchestrator.service.SuspendWorkflowService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SuspendWorkflowHandler implements Handler<RoutingContext> {

    @Autowired
    private SuspendWorkflowService suspendWorkflowService;
    @Override
    public void handle(RoutingContext routingContext) {
        final String asyncId = routingContext.queryParams().get("asyncId");
        suspendWorkflowService.suspend(asyncId).setHandler(handler -> {
           if(handler.succeeded()) {
               routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
           } else {
               routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
           }
        });
    }
}

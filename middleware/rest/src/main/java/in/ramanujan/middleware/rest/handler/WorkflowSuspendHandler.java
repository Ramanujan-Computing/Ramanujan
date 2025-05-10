package in.ramanujan.middleware.rest.handler;

import in.ramanujan.middleware.service.WorkflowSuspendService;
import io.vertx.ext.web.RoutingContext;

public class WorkflowSuspendHandler extends CommonHandler {

    public WorkflowSuspendService workflowSuspendService;

    @Override
    public void handle(RoutingContext routingContext) {
        final String asyncId = routingContext.queryParams().get("asyncId");
        try {
            workflowSuspendService.suspendWf(asyncId).setHandler(handler -> {
               if(handler.succeeded()) {
                   respondSuccess(routingContext, asyncId);
               } else {
                   respondInternalServerError(routingContext, handler.cause());
               }
            });
        } catch (Exception e) {
            respondInternalServerError(routingContext, e);
        }
    }
}

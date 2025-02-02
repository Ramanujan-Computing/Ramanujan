package in.robinhood.ramanujan.middleware.rest.handler;

import in.robinhood.ramanujan.middleware.base.pojo.ApiResponse;
import in.robinhood.ramanujan.middleware.service.WorkflowSuspendService;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowSuspendHandler extends CommonHandler {

    @Autowired
    private WorkflowSuspendService workflowSuspendService;

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

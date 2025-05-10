package in.ramanujan.middleware.rest.handler;

import in.ramanujan.middleware.service.DebugInformationFetchService;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;

public class DebugInformationFetchHandler extends CommonHandler {

    public DebugInformationFetchService debugInformationFetchService;

    @Override
    public void handle(RoutingContext routingContext) {
        MultiMap map = routingContext.queryParams();
        String asyncId = map.get("asyncId");
        String dagElementid = map.get("dagElementId");
        debugInformationFetchService.getDebugInformation(asyncId, dagElementid).setHandler(handler -> {
           if(handler.succeeded()) {
               respondSuccess(routingContext, handler.result());
           } else {
               respondInternalServerError(routingContext, handler.cause());
           }
        });
    }
}

package in.robinhood.ramanujan.middleware.rest.handler;

import in.robinhood.ramanujan.middleware.service.DebugInformationFetchService;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DebugInformationFetchHandler extends CommonHandler {

    @Autowired
    private DebugInformationFetchService debugInformationFetchService;

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

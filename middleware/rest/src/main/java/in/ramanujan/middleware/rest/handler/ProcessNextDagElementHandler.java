package in.ramanujan.middleware.rest.handler;

import in.ramanujan.middleware.base.pojo.ApiResponse;
import in.ramanujan.middleware.service.ProcessNextDagElementService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.util.HashSet;
import java.util.Set;

public class ProcessNextDagElementHandler extends CommonHandler {

    public ProcessNextDagElementService processNextDagElementService;

    private Logger logger = LoggerFactory.getLogger(ProcessNextDagElementHandler.class);

    private Set<String> dagElementIds = new HashSet<>();

    private synchronized void check(String dagElementId, Boolean remove) {
        if(remove) {
            dagElementIds.remove(dagElementId);
            return;
        }
        if(dagElementIds.contains(dagElementId)) {
            logger.info("DUPLICATE IN SYSTEM " + dagElementId);
        }
        dagElementIds.add(dagElementId);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final JsonObject jsonObject = routingContext.getBodyAsJson();
        final String asyncId = jsonObject.getString("asyncId");
        final String dagElementId = jsonObject.getString("dagElementId");
        final String source = jsonObject.getString("source");
        final Boolean toBeDebugged = jsonObject.getBoolean("toBeDebugged");

        try {
            check(dagElementId, false);
            processNextDagElementService.processNextElement(asyncId, dagElementId, routingContext.vertx(), toBeDebugged).setHandler(handler -> {
                if ("kafka".equalsIgnoreCase(source)) {
                    if (handler.succeeded()) {
                        check(dagElementId, true);
                        respondSuccess(routingContext, new ApiResponse(HttpResponseStatus.OK.toString(), null));
                    } else {
                        logger.error(handler.cause());
                        respondInternalServerError(routingContext,
                                new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(), handler.cause()));
                    }
                }
            });
            if("middleware".equalsIgnoreCase(source)) {
                respondSuccess(routingContext, new ApiResponse(HttpResponseStatus.OK.toString(), null));
            }
        } catch (Exception e) {
            respondInternalServerError(routingContext,
                    new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(), e));
        }
    }
}

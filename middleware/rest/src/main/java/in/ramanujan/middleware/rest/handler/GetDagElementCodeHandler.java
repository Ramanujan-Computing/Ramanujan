package in.ramanujan.middleware.rest.handler;

import in.ramanujan.middleware.base.pojo.ApiResponse;
import in.ramanujan.middleware.service.GetDagElementCodeService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetDagElementCodeHandler extends CommonHandler {
    @Autowired
    private GetDagElementCodeService getDagElementCodeService;

    @Override
    public void handle(RoutingContext routingContext) {
        MultiMap map = routingContext.queryParams();
        getDagElementCodeService.getCode(map.get("asyncId"), map.get("dagElementId")).setHandler(handler -> {
           if(handler.succeeded()) {
               respondSuccess(routingContext, new ApiResponse(HttpResponseStatus.OK.toString(), ""));
           } else {
               respondInternalServerError(routingContext, new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(), ""));
           }
        });
    }
}

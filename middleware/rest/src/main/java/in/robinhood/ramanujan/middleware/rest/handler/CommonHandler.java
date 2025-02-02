package in.robinhood.ramanujan.middleware.rest.handler;

import in.robinhood.ramanujan.middleware.base.pojo.ApiResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class CommonHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext routingContext) {

    }

    protected void respondSuccess(final RoutingContext routingContext, final Object apiResponseData) {
        final ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.OK.toString(), apiResponseData);
        routingContext.response().setStatusCode(HttpResponseStatus.OK.code())
                .end(JsonObject.mapFrom(apiResponse).toString());
    }

    protected void respondInternalServerError(final RoutingContext routingContext, final Object apiResponseData) {
        final ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(),
                apiResponseData);
        routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                .end(JsonObject.mapFrom(apiResponse).toString());
    }

    protected void respondBadRequest(final RoutingContext routingContext, final Object apiResponseData) {
        final ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.BAD_REQUEST.toString(),
                apiResponseData);
        routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                .end(JsonObject.mapFrom(apiResponse).toString());
    }
}

package in.ramanujan.middleware.rest.handler;

import in.ramanujan.middleware.base.pojo.ApiResponse;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import in.ramanujan.middleware.service.TaskStatusService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class StatusHandler implements Handler<RoutingContext> {

    public TaskStatusService taskStatusService;

    @Override
    public void handle(RoutingContext event) {

        String taskId = event.queryParams().get("uuid");

        taskStatusService.getAsyncTaskStatus(taskId).setHandler(handler -> {
           if(handler.succeeded()) {
               AsyncTask asyncTask = handler.result();
               ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.OK.toString(), asyncTask);
               event.response().setStatusCode(HttpResponseStatus.OK.code()).end(
                       JsonObject.mapFrom(apiResponse).toString()
               );
           } else {

               event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                       JsonObject.mapFrom(new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(),
                               handler.cause())).toString()
               );
           }
        });
    }
}

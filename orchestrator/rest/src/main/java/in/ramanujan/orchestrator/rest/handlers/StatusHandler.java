package in.ramanujan.orchestrator.rest.handlers;

import in.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.service.TaskStatusService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StatusHandler implements Handler<RoutingContext> {

    @Autowired
    private TaskStatusService taskStatusService;

    @Override
    public void handle(RoutingContext event) {

        String taskId = event.queryParams().get("uuid");

        taskStatusService.getAsyncTaskStatus(taskId).setHandler(handler -> {
           if(handler.succeeded()) {
               AsyncTask asyncTask = handler.result();
               ApiResponse apiResponse = new ApiResponse(asyncTask.getStatus(), asyncTask);
               event.response().setStatusCode(HttpResponseStatus.OK.code()).end(
                       JsonObject.mapFrom(apiResponse).toString()
               );
           } else {
               event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
           }
        });
    }
}

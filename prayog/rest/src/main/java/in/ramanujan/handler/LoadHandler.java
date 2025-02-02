package in.ramanujan.handler;

import in.ramanujan.proyog.service.LoadService;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LoadHandler implements Handler<RoutingContext> {

    @Autowired
    private LoadService loadService;

    @Override
    public void handle(RoutingContext event) {
        MultiMap multiMap = event.queryParams();
        int users = Integer.parseInt(multiMap.get("users"));
        JsonObject execCode = event.getBodyAsJson();
        loadService.startTest(users, execCode).setHandler(handler -> {
            if(handler.succeeded()) {
                event.response().setStatusCode(200).end();
            } else {
                event.response().setStatusCode(500).end();
            }
        });
    }
}

package in.ramanujan.handler;

import in.ramanujan.proyog.service.SpawnService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class StartSpawnDeviceHandler implements Handler<RoutingContext> {

    @Autowired
    private SpawnService spawnService;

    @Override
    public void handle(RoutingContext event) {
        MultiMap queryParams = event.queryParams();
        int deviceToMock = Integer.parseInt(queryParams.get("devices"));
        spawnService.startSpawn(deviceToMock, event.vertx()).setHandler(handler -> {
            if(handler.succeeded()) {
                event.response().setStatusCode(HttpResponseStatus.OK.code()).end();
            } else {
                event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();;
            }
        });
    }
}

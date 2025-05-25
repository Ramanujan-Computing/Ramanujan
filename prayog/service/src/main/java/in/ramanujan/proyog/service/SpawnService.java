package in.ramanujan.proyog.service;

import in.ramanujan.data.DeviceMock;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpawnService {
    @Autowired
    private DeviceMock deviceMock;

    public void init() {
        for(int i= 0; i < 2* Runtime.getRuntime().availableProcessors(); i++) {
            deviceMock.mock(Vertx.vertx());
        }
    }

    public Future<Void> startSpawn(int devicesToMock, Vertx vertx) {
        Future<Void> future = Future.future();
        List<Future> futureList = new ArrayList<>();
        for(int i=0; i< devicesToMock; i++) {
            futureList.add(deviceMock.mock(vertx));
        }
        CompositeFuture.all(futureList).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }

    public Future<Void> stopSpawn(Vertx vertx) {
        deviceMock.stopMock(vertx);
        return Future.succeededFuture();
    }
}

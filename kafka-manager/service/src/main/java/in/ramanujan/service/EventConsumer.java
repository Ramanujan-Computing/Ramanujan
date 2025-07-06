package in.ramanujan.service;


import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.data.MiddlewareClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {

    @Autowired
    private MiddlewareClient middlewareClient;

    @Autowired
    private EventProducer eventProducer;

    public Future<Void> consume(final CheckStatusQueueEvent checkStatusQueueEvent, Vertx vertx) {
        Future<Void> future = Future.future();
        final String asyncId = checkStatusQueueEvent.getAsyncId();
        final String dagElementId = checkStatusQueueEvent.getDagElementId();
        final Boolean toBeDebugged = checkStatusQueueEvent.getToBeDebugged();
        middlewareClient.callMiddlewareProcessNextElementApi(asyncId, dagElementId, toBeDebugged, vertx).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete();
           } else {
               eventProducer.produce(checkStatusQueueEvent).setHandler(produceHandler -> {
                   future.complete();
               });
           }
        });
        return future;
    }
}

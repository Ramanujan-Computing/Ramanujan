package in.ramanujan.service;

import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.data.MiddlewareClient;
import in.ramanujan.data.QueueingDao;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
@Component
public class EventProducer {
    
    @Autowired
    private MiddlewareClient middlewareClient;

    @Autowired
    private QueueingDao queueingDao;

    
    public Future<Void> produce(final CheckStatusQueueEvent kafkaEvent) {
        Future<Void> future = Future.future();
        queueingDao.produce(kafkaEvent).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}

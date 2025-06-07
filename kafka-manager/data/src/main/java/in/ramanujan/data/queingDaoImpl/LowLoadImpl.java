package in.ramanujan.data.queingDaoImpl;

import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.base.pojo.CheckStatusQueueEventWithMetadata;
import in.ramanujan.data.QueueingDao;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

public class LowLoadImpl extends QueueDaoImpl {

    private List<CheckStatusQueueEventWithMetadata> events = new ArrayList<>();

    private synchronized Object actOnEvents(CheckStatusQueueEvent checkStatusQueueEvent, Boolean add, Boolean get) {
        if(add) {
            CheckStatusQueueEventWithMetadata checkStatusQueueEventWithMetadata = new CheckStatusQueueEventWithMetadata();
            checkStatusQueueEventWithMetadata.setCheckStatusQueueEvent(checkStatusQueueEvent);
            checkStatusQueueEventWithMetadata.setMetadata("metadata");
            events.add(checkStatusQueueEventWithMetadata);
            return null;
        }
        if(get) {
            List<CheckStatusQueueEventWithMetadata> result = events;
            events = new ArrayList<>();
            return result;
        }
        return null;
    }

    @Override
    public Future<Void> produce(CheckStatusQueueEvent kafkaEvent) {
        actOnEvents(kafkaEvent, true, false);
        return Future.succeededFuture();
    }

    @Override
    public Future<List<CheckStatusQueueEventWithMetadata>> consume() {
        return Future.succeededFuture((List<CheckStatusQueueEventWithMetadata>)actOnEvents(null, false, true));
    }

    @Override
    public Future<Void> subscribe() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> commit(Object metadata) {
        return Future.succeededFuture();
    }
}

package in.ramanujan.data;

import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.base.pojo.CheckStatusQueueEventWithMetadata;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface QueueingDao {
    public Future<Void> produce(final CheckStatusQueueEvent kafkaEvent);
    public Future<List<CheckStatusQueueEventWithMetadata>> consume();
    public Future<Void> subscribe();
    public Future<Void> commit(Object metadata);
}

package in.ramanujan.data.queingDaoImpl;

import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.base.pojo.CheckStatusQueueEventWithMetadata;
import in.ramanujan.data.QueueingDao;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QueueDaoImpl implements QueueingDao {
    private QueueDaoImpl queueDaoImpl;

    public static final String QUEUE_TYPE = "queue.type";

    public static enum QueueType {
        GCP, LOCAL;

        public static QueueType fromString(String type) {
            if (type.equalsIgnoreCase("GCP")) {
                return GCP;
            } else if (type.equalsIgnoreCase("LOCAL")) {
                return LOCAL;
            } else {
                throw new IllegalArgumentException("Unknown QueueType: " + type);
            }
        }
    }

    public void init(QueueType queueType) {
        if (queueType == QueueType.GCP) {
            queueDaoImpl = new PubSubIMpl();
        } else if (queueType == QueueType.LOCAL) {
            queueDaoImpl = new LowLoadImpl();
        } else {
            throw new IllegalArgumentException("Unknown QueueType: " + queueType);
        }
    }


    @Override
    public Future<Void> produce(CheckStatusQueueEvent kafkaEvent) {
        return queueDaoImpl.produce(kafkaEvent);
    }

    @Override
    public Future<List<CheckStatusQueueEventWithMetadata>> consume() {
        return queueDaoImpl.consume();
    }

    @Override
    public Future<Void> subscribe() {
        return queueDaoImpl.subscribe();
    }

    @Override
    public Future<Void> commit(Object metadata) {
        return queueDaoImpl.commit(metadata);
    }
}

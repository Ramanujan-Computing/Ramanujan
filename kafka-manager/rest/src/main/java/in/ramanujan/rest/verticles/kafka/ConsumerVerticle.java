package in.ramanujan.rest.verticles.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.base.enums.Topics;
import in.ramanujan.base.pojo.CheckStatusQueueEventWithMetadata;
import in.ramanujan.data.QueueingDao;
import in.ramanujan.data.queingDaoImpl.KafkaImpl;
import in.ramanujan.service.EventConsumer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConsumerVerticle extends AbstractVerticle {

    @Autowired
    private EventConsumer eventConsumer;
    
    @Autowired
    private QueueingDao queueingDao;

    private ObjectMapper objectMapper = new ObjectMapper();

    Logger logger= LoggerFactory.getLogger(ConsumerVerticle.class);

    final String topicName = Topics.next_element_topic.name();
    final Long pollingIntervalInMillis = 2_000L;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        logger.info("Consumer is up");
        
        queueingDao.subscribe().setHandler(handler -> {
           if(handler.succeeded()) {
               startConsumption();
           }
        });

        startConsumption();
    }

    private void startConsumption() {
        final BlockingWrapper blockingWrapper = new BlockingWrapper();
        blockingWrapper.unblock();

        vertx.setPeriodic(pollingIntervalInMillis, handler -> {
            if(blockingWrapper.getBlocked()) {
                return;
            }
            blockingWrapper.block();
            queueingDao.consume().setHandler(consumeHandler -> {
                if(consumeHandler.succeeded()) {
                    List<Future> consumeFutureList = new ArrayList<>();
                    List<CheckStatusQueueEventWithMetadata> checkStatusQueueEventWithMetadataList = consumeHandler.result();
                    if(checkStatusQueueEventWithMetadataList == null) {
                        blockingWrapper.unblock();
                        return;
                    }
                    for(CheckStatusQueueEventWithMetadata checkStatusQueueEventWithMetadata : checkStatusQueueEventWithMetadataList) {
                        consumeFutureList.add(eventConsumer.consume(checkStatusQueueEventWithMetadata.getCheckStatusQueueEvent(), vertx));
                    }

                    CompositeFuture.all(consumeFutureList).setHandler(consumerListHandler -> {
                        if(checkStatusQueueEventWithMetadataList.size() == 0) {
                            blockingWrapper.unblock();
                            return;
                        }
                        Object metadata = (queueingDao.getClass() == KafkaImpl.class) ?
                                checkStatusQueueEventWithMetadataList.get(checkStatusQueueEventWithMetadataList.size() -1).getMetadata() :
                                getPubSubMetadata(checkStatusQueueEventWithMetadataList);
                        queueingDao.commit(metadata).setHandler(commitHandler -> {
                            blockingWrapper.unblock();
                        });
                    });
                } else {
                    logger.error(consumeHandler.cause());
                    blockingWrapper.unblock();
                }

            });
        });
    }

    private List<String> getPubSubMetadata(List<CheckStatusQueueEventWithMetadata> checkStatusQueueEventWithMetadataList) {
        List<String> list = new ArrayList<>();
        for(CheckStatusQueueEventWithMetadata checkStatusQueueEventWithMetadata : checkStatusQueueEventWithMetadataList) {
            list.add((String) checkStatusQueueEventWithMetadata.getMetadata());
        }
        return list;
    }

    @Data
    private class BlockingWrapper {
        private Boolean blocked;

        public void block() {
            blocked = true;
        }

        public void unblock() {
            blocked = false;
        }
    }

}

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
    final Long pollingIntervalInMillis = 2_00L;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        logger.info("Consumer is up");
        
        queueingDao.subscribe().setHandler(handler -> {
           if(handler.succeeded()) {
               startConsumption();
               startPromise.complete();
           } else {
               startPromise.fail(handler.cause());
           }
        });
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
                    List<CheckStatusQueueEventWithMetadata> successAckId = java.util.Collections.synchronizedList(new ArrayList<>());
                    for(CheckStatusQueueEventWithMetadata checkStatusQueueEventWithMetadata : checkStatusQueueEventWithMetadataList) {
                        Future future  = Future.future();
                        consumeFutureList.add(future);
                        eventConsumer.consume(checkStatusQueueEventWithMetadata.getCheckStatusQueueEvent(), vertx).setHandler(actualConsumer ->{
                            if(actualConsumer.succeeded()) {
                                successAckId.add(checkStatusQueueEventWithMetadata);
                                future.complete();
                            } else {
                                logger.error("Failed to consume event: " + checkStatusQueueEventWithMetadata.getCheckStatusQueueEvent(), actualConsumer.cause());
                                future.fail(actualConsumer.cause());
                            }
                        });
                        }

                    CompositeFuture.join(consumeFutureList).setHandler(consumerListHandler -> {
                        if(successAckId.isEmpty()) {
                            blockingWrapper.unblock();
                            return;
                        }
                        Object metadata = (queueingDao.getClass() == KafkaImpl.class) ?
                                successAckId.get(successAckId.size() -1).getMetadata() :
                                getPubSubMetadata(successAckId);
                        queueingDao.commit(metadata).setHandler(commitHandler -> {
                            if (commitHandler.failed()) {
                                logger.error("Failed to acknowledge consumed events", commitHandler.cause());
                            }
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

        private long blockedLastTime;

        public synchronized void block() {
            blocked = true;
            blockedLastTime = System.currentTimeMillis();
        }

        public synchronized void unblock() {
            blocked = false;
        }

        public synchronized Boolean getBlocked() {
            if(blocked == null || !blocked) {
                return false;
            }
            if(System.currentTimeMillis() - blockedLastTime > 60_000L) {
                logger.warn("Queue batch is still processing; waiting without restarting the server");
                blockedLastTime = System.currentTimeMillis();
            }
            return blocked;
        }
    }

}

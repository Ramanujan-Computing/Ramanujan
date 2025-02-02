package in.ramanujan.data.queingDaoImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.base.enums.Topics;
import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.base.pojo.CheckStatusQueueEventWithMetadata;
import in.ramanujan.data.QueueingDao;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class KafkaImpl implements QueueingDao {

    private KafkaProducer<String, String> kafkaProducer;
    private KafkaConsumer<String, String> consumer;

    final String topicName = Topics.next_element_topic.name();

    Logger logger= LoggerFactory.getLogger(KafkaImpl.class);

    private ObjectMapper objectMapper = new ObjectMapper();
    final Long pollingIntervalInMillis = 1000L;

    private KafkaProducer<String, String> getKafkaProducer() {
        if(kafkaProducer == null) {
            Map<String, String> config = new HashMap<>();
            config.put("bootstrap.servers", "localhost:9092");
            config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            config.put("acks", "1");
            kafkaProducer = KafkaProducer.create(Vertx.vertx(), config);
        }
        return kafkaProducer;
    }

    private KafkaConsumer<String, String> getConsumer() {
        if(consumer == null) {
            Map<String, String> config = new HashMap<>();
            config.put("bootstrap.servers", "localhost:9092");
            config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            config.put("group.id", "my_group");
            config.put("auto.offset.reset", "earliest");
            config.put("enable.auto.commit", "false");

            consumer = KafkaConsumer.create(Vertx.vertx(), config);

        }
        return consumer;
    }


    @Override
    public Future<Void> produce(CheckStatusQueueEvent kafkaEvent) {
        Future<Void> future = Future.future();
        KafkaProducerRecord<String, String> producerRecord = KafkaProducerRecord
                .create(Topics.next_element_topic.name(), JsonObject.mapFrom(kafkaEvent).toString());
        getKafkaProducer().write(producerRecord, produceHandler -> {
            if(produceHandler.succeeded()) {
                future.complete();
            } else {
                produce(kafkaEvent);
                future.complete();
            }
        });
        return future;
    }

    @Override
    public Future<Void> subscribe() {
        Future<Void> future = Future.future();
        getConsumer().subscribe(topicName, handler -> {
            if(handler.succeeded()) {
                logger.info(topicName + " has been subscribed successfully");
                future.complete();
            } else {
                logger.error(topicName + " has NOT been subscribed successfully", handler.cause());
                future.fail(handler.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Void> commit(Object metadata) {
        Future<Void> future = Future.future();
        KafkaConsumerRecord lastRecord = (KafkaConsumerRecord) metadata;
        TopicPartition topicPartition = new TopicPartition(lastRecord.topic(), lastRecord.partition());
        OffsetAndMetadata offsetAndMetadata = new OffsetAndMetadata(lastRecord.offset(), "");
        Map<TopicPartition, OffsetAndMetadata> commitMap = new HashMap<TopicPartition, OffsetAndMetadata>() {{
            put(topicPartition, offsetAndMetadata);
        }};
        getConsumer().commit(commitMap, commitHandler -> {
            future.complete();
        });
        return future;
    }

    @Override
    public Future<List<CheckStatusQueueEventWithMetadata>> consume() {
        Future<List<CheckStatusQueueEventWithMetadata>> future = Future.future();
        List<CheckStatusQueueEventWithMetadata> result = new ArrayList<>();
        getConsumer().poll(pollingIntervalInMillis, pollHandler -> {
            if (pollHandler.succeeded()) {
                final KafkaConsumerRecords kafkaConsumerRecords = pollHandler.result();
                List<Future> consumeFutureList = new ArrayList<>();
                for (int i = 0; i < kafkaConsumerRecords.size(); i++) {
                    final KafkaConsumerRecord kafkaConsumerRecord = kafkaConsumerRecords.recordAt(i);
                    try {
                        final CheckStatusQueueEvent checkStatusQueueEvent = objectMapper
                                .readValue(kafkaConsumerRecord.value().toString(), CheckStatusQueueEvent.class);
                        CheckStatusQueueEventWithMetadata checkStatusQueueEventWithMetadata = new CheckStatusQueueEventWithMetadata();
                        checkStatusQueueEventWithMetadata.setCheckStatusQueueEvent(checkStatusQueueEvent);
                        checkStatusQueueEventWithMetadata.setMetadata(kafkaConsumerRecord);
                        result.add(checkStatusQueueEventWithMetadata);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                future.complete(result);
            } else {
                future.complete(new ArrayList<>());
            }
        });
        return future;
    }
}

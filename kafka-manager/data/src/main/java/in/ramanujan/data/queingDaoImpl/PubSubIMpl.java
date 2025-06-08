package in.ramanujan.data.queingDaoImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import in.ramanujan.base.configuration.ConfigConstant;
import in.ramanujan.base.configuration.ConfigKey;
import in.ramanujan.base.configuration.ConfigurationGetter;
import in.ramanujan.base.enums.Topics;
import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.base.pojo.CheckStatusQueueEventWithMetadata;
import in.ramanujan.data.QueueingDao;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//@Component
public class PubSubIMpl extends QueueDaoImpl {

    private final String projectId = "ramanujan-340512";
    private final String subscriptionId = "next_element_topic-sub";
    private final String topic = Topics.next_element_topic.name();;

    private final ObjectMapper objectMapper = new ObjectMapper();
    Logger logger= LoggerFactory.getLogger(PubSubIMpl.class);

    private Publisher publisher;

    private String subscribeName;
    private SubscriberStub subscriber;
    private final int numOfMessages = 100;

    private final Set<String> messageIdSeen = new HashSet<>();

    private void initSubscribeName() throws Exception {
        SubscriberStubSettings.Builder builderWithTransportChannelProvider
                = SubscriberStubSettings.newBuilder().setTransportChannelProvider(
                SubscriberStubSettings.defaultGrpcTransportProviderBuilder()
                        .setMaxInboundMessageSize(20 * 1024 * 1024) // 20MB (maximum message size).
                        .build()
        );

        if(ConfigConstant.PROD.equalsIgnoreCase(ConfigurationGetter.getString(ConfigKey.ENV))) {
            builderWithTransportChannelProvider.setCredentialsProvider(() -> {
                return getCred();
            });
        }

       SubscriberStubSettings subscriberStubSettings = builderWithTransportChannelProvider.build();

        subscriber = GrpcSubscriberStub.create(subscriberStubSettings);
        subscribeName = ProjectSubscriptionName.format(projectId, subscriptionId);
    }

    private GoogleCredentials getCred() throws IOException {
        return GoogleCredentials.fromStream(new FileInputStream("/pubSubKey.json"))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
    }


    private Publisher getPublisher() throws Exception {
        if(publisher == null) {
            TopicName topicName = TopicName.of(projectId, topic);
            Publisher.Builder builder = Publisher.newBuilder(topicName);
            if(ConfigConstant.PROD.equalsIgnoreCase(ConfigurationGetter.getString(ConfigKey.ENV))) {
                builder.setCredentialsProvider(() -> {
                    return getCred();
                });
            }
            publisher = builder.build();
        }
        return publisher;
    }


    public static void main(String[] args) throws Exception {
        CheckStatusQueueEvent checkStatusQueueEvent = new CheckStatusQueueEvent();
        checkStatusQueueEvent.setAsyncId("asyncId");
        checkStatusQueueEvent.setDagElementId("dagElementId");
        System.out.println(JsonObject.mapFrom(checkStatusQueueEvent).toString());
        ByteString bytes = ByteString.copyFromUtf8(JsonObject.mapFrom(checkStatusQueueEvent).toString());
        System.out.println(bytes);
        System.out.println(bytes.toStringUtf8());
    }



    @Override
    public Future<Void> produce(CheckStatusQueueEvent kafkaEvent) {
        Future<Void> future = Future.future();
        try {
            ByteString data = ByteString.copyFromUtf8(JsonObject.mapFrom(kafkaEvent).toString());
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

            // Once published, returns a server-assigned message id (unique within the topic)
            ApiFuture<String> messageIdFuture = getPublisher().publish(pubsubMessage);
            Vertx.currentContext().executeBlocking(blocking -> {
                try {
                    messageIdFuture.get();
                    blocking.complete();
                } catch (Exception e) {
                    logger.error("Error happened while publishing to pubsub", e);
                    blocking.fail(e);
                }
            }, false, handler -> {
                if (handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<List<CheckStatusQueueEventWithMetadata>> consume() {
        Future<List<CheckStatusQueueEventWithMetadata>> future = Future.future();
        Vertx.currentContext().executeBlocking(blocking -> {
            try {
                blocking.complete(getPullResponse());
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                try {
                    PullResponse pullResponse = (PullResponse) handler.result();
                    if (pullResponse.getReceivedMessagesList().isEmpty()) {
                        logger.info("Nothing to consume");
                        future.complete(new ArrayList<>());
                        return;
                    }
                    List<CheckStatusQueueEventWithMetadata> list = new ArrayList<>();
                    for (ReceivedMessage message : pullResponse.getReceivedMessagesList()) {
                        /**
                         * data: "{\n  \"kafkaEventType\" : null,\n  \"dagElementId\" : \"e537d572-1c13-4a4d-b43b-4b908d72aab3\",\n  \"asyncId\" : \"f12d1c6f-9e6f-4646-b964-30b551d740b1\"\n}"
                         * message_id: "4077843053054254"
                         * publish_time {
                         *   seconds: 1650116672
                         *   nanos: 599000000
                         * }
                         * */
                        try {
                            ByteString bytes = message.getMessage().getData();
                            final String messageId = message.getMessage().getMessageId();
                            if(messageIdSeen.contains(messageId)) {
                                logger.info("REPETITIVE MESSAGE_ID + " + messageId);
                            }
                            messageIdSeen.add(messageId);
                            CheckStatusQueueEventWithMetadata checkStatusQueueEventWithMetadata = new CheckStatusQueueEventWithMetadata();
                            checkStatusQueueEventWithMetadata.setCheckStatusQueueEvent(objectMapper.readValue(bytes.toStringUtf8(), CheckStatusQueueEvent.class));
                            checkStatusQueueEventWithMetadata.setMetadata(message.getAckId());
                            logger.info("Consumed: " + bytes);
                            list.add(checkStatusQueueEventWithMetadata);
                        } catch (Exception e) {
                            logger.error("Error happened while reading pubsub message: ", e);
                        }
                    }
                    future.complete(list);
                } catch (Exception e) {
                    future.fail(e);
                }
            } else {
                logger.error("Consumption failed", handler.cause());
                future.fail(handler.cause());
            }
        });
        return future;
    }

    private PullResponse getPullResponse() {
        PullRequest pullRequest =
                PullRequest.newBuilder()
                        .setMaxMessages(numOfMessages)
                        .setSubscription(subscribeName)
                        .build();

        // Use pullCallable().futureCall to asynchronously perform this operation.
        PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
        return pullResponse;
    }

    @Override
    public Future<Void> subscribe() {
        Future<Void> future = Future.future();
        Vertx.currentContext().executeBlocking(blocking -> {
            try {
                initSubscribeName();
                blocking.complete();
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                logger.info("subscribed");
                future.complete();
            } else {
                logger.error("Error in subscibing: ", handler.cause());
                future.fail(handler.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Void> commit(Object metadata) {
        Future<Void> future = Future.future();
        List<String> ackIds = (List<String>) metadata;
        logger.info("Commit process to start");
        Vertx.currentContext().executeBlocking(blocking -> {
            try {
                logger.info("Commit in parallel thread process start");
                AcknowledgeRequest acknowledgeRequest =
                        AcknowledgeRequest.newBuilder()
                                .setSubscription(subscribeName)
                                .addAllAckIds(ackIds)
                                .build();

                // Use acknowledgeCallable().futureCall to asynchronously perform this operation.
                subscriber.acknowledgeCallable().call(acknowledgeRequest);
                logger.info("commit successful");
                blocking.complete();
            } catch (Exception e) {
                logger.error("COMMIT FAILED", e);
                blocking.fail(e);
            }
        }, false, handler -> {
            future.complete();
        });
        return future;
    }
}

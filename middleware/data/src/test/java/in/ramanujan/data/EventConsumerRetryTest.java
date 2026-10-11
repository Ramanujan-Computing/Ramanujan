package in.ramanujan.data;

import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.service.EventConsumer;
import in.ramanujan.service.EventProducer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class EventConsumerRetryTest {
    private EventConsumer consumer(Future<Void> processing, Future<Void> published,
                                   AtomicInteger attempts) throws Exception {
        EventConsumer consumer = new EventConsumer();
        Field client = EventConsumer.class.getDeclaredField("middlewareClient");
        client.setAccessible(true);
        client.set(consumer, new MiddlewareClient() {
            @Override
            public Future<Void> callMiddlewareProcessNextElementApi(String asyncId, String dagId,
                                                                    Boolean debug, Vertx vertx) {
                return processing;
            }
        });
        Field producer = EventConsumer.class.getDeclaredField("eventProducer");
        producer.setAccessible(true);
        producer.set(consumer, new EventProducer() {
            @Override
            public Future<Void> produce(CheckStatusQueueEvent event) {
                attempts.incrementAndGet();
                return published;
            }
        });
        return consumer;
    }

    @Test
    public void successfulProcessingDoesNotRepublish() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Future<Void> result = consumer(Future.succeededFuture(), Future.succeededFuture(), attempts)
                .consume(new CheckStatusQueueEvent(), null);
        assertTrue(result.succeeded());
        assertEquals(0, attempts.get());
    }

    @Test
    public void acknowledgesOnlyAfterRetryIsDurable() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Future<Void> publication = Future.future();
        Future<Void> result = consumer(Future.failedFuture("not committed yet"), publication, attempts)
                .consume(new CheckStatusQueueEvent(), null);
        assertFalse(result.isComplete());
        assertEquals(1, attempts.get());
        publication.complete();
        assertTrue(result.succeeded());
    }

    @Test
    public void failedPublicationLeavesOriginalMessageUnacknowledged() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Future<Void> result = consumer(Future.failedFuture("not committed yet"),
                Future.failedFuture("publish unavailable"), attempts)
                .consume(new CheckStatusQueueEvent(), null);
        assertTrue(result.failed());
        assertEquals("publish unavailable", result.cause().getMessage());
    }

    @Test
    public void retryCanBeProcessedByAnotherConsumer() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Future<Void> first = consumer(Future.failedFuture("not committed yet"),
                Future.succeededFuture(), attempts).consume(new CheckStatusQueueEvent(), null);
        Future<Void> second = consumer(Future.succeededFuture(),
                Future.succeededFuture(), attempts).consume(new CheckStatusQueueEvent(), null);
        assertTrue(first.succeeded());
        assertTrue(second.succeeded());
        assertEquals(1, attempts.get());
    }
}

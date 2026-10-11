package in.ramanujan.middleware.rest.verticles;

import in.ramanujan.rest.verticles.kafka.ConsumerVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class MainVerticleStartupTest {
    private final Vertx vertx = Vertx.vertx();

    @AfterEach
    public void closeVertx() throws Exception {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        vertx.close(handler -> closed.complete(null));
        closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void consumerWaitsForHttpReadiness() throws Exception {
        CompletableFuture<Future<Void>> initializing = new CompletableFuture<>();
        AtomicBoolean consumed = new AtomicBoolean();
        HttpVerticle http = new HttpVerticle() {
            @Override
            public void start(Future<Void> started) {
                initializing.complete(started);
            }
        };
        ConsumerVerticle consumer = new ConsumerVerticle() {
            @Override
            public void start(io.vertx.core.Promise<Void> started) {
                consumed.set(true);
                started.complete();
            }
        };
        Future<Void> ready = MainVerticle.deployApplicationVerticles(vertx, http, consumer);
        Future<Void> httpReady = initializing.get(5, TimeUnit.SECONDS);
        assertFalse(consumed.get());
        assertFalse(ready.isComplete());
        httpReady.complete();
        assertTrue(await(ready).succeeded());
        assertTrue(consumed.get());
    }

    @Test
    public void failedInitializationNeverStartsConsumer() throws Exception {
        AtomicBoolean consumed = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("initialization failed");
        HttpVerticle http = new HttpVerticle() {
            @Override
            public void start(Future<Void> started) {
                started.fail(failure);
            }
        };
        ConsumerVerticle consumer = new ConsumerVerticle() {
            @Override
            public void start(io.vertx.core.Promise<Void> started) {
                consumed.set(true);
                started.complete();
            }
        };
        assertSame(failure, await(MainVerticle.deployApplicationVerticles(vertx, http, consumer)).cause());
        assertFalse(consumed.get());
    }

    @Test
    public void consumerStartupFailureFailsMainStartup() throws Exception {
        IllegalStateException failure = new IllegalStateException("subscription failed");
        HttpVerticle http = new HttpVerticle() {
            @Override
            public void start(Future<Void> started) {
                started.complete();
            }
        };
        ConsumerVerticle consumer = new ConsumerVerticle() {
            @Override
            public void start(io.vertx.core.Promise<Void> started) {
                started.fail(failure);
            }
        };
        assertSame(failure, await(MainVerticle.deployApplicationVerticles(vertx, http, consumer)).cause());
    }

    private static <T> AsyncResult<T> await(Future<T> future) throws Exception {
        CompletableFuture<AsyncResult<T>> result = new CompletableFuture<>();
        future.setHandler(result::complete);
        return result.get(5, TimeUnit.SECONDS);
    }
}

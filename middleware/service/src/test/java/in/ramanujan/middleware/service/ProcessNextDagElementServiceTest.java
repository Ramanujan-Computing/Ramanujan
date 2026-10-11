package in.ramanujan.middleware.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.data.db.dao.OrchestratorAsyncTaskDao;
import in.ramanujan.data.db.dao.DagElementDao;
import in.ramanujan.data.db.dao.StorageDao;
import in.ramanujan.data.db.dao.VariableValueDao;
import in.ramanujan.utils.Constants;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ProcessNextDagElementServiceTest {
    private final Vertx vertx = Vertx.vertx();
    private final ProcessNextDagElementService service = new ProcessNextDagElementService();
    private final VariableValueDao variables = mock(VariableValueDao.class);
    private final RecordingStorage storage = new RecordingStorage();

    @After
    public void closeVertx() throws Exception {
        storage.release.countDown();
        CompletableFuture<Void> closed = new CompletableFuture<>();
        vertx.close(handler -> closed.complete(null));
        closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void serializesWritesAndWaitsForStorageCompletion() throws Exception {
        inject(service, "variableValueDao", variables);
        inject(service, "storageDao", storage);
        BlockingQueue<Future<Void>> arrayWrites = new ArrayBlockingQueue<>(2);
        when(variables.storeArrayValueBatch(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(call -> {
                    Future<Void> write = Future.future();
                    arrayWrites.add(write);
                    return write;
                });
        Future<Void> scalarWrite = Future.future();
        when(variables.updateVariablesBatch(anyString(), anyList())).thenReturn(scalarWrite);
        storage.setContext(vertx.getOrCreateContext(), null);
        storage.release = new CountDownLatch(1);
        AtomicBoolean serializedOnEventLoop = new AtomicBoolean(true);
        Map<String, Object> result = resultWithArrays();
        result.put("scalar", new ThreadCheckedValue(serializedOnEventLoop));
        Future<Void> refreshed = service.refreshVariables("async", "vertex", result, vertx);
        Future<Void> first = arrayWrites.poll(5, TimeUnit.SECONDS);
        assertNotNull(first);
        verify(variables, times(1)).storeArrayValueBatch(anyString(), anyString(), anyString(), anyMap());
        verify(variables, never()).updateVariablesBatch(anyString(), anyList());
        complete(first);
        Future<Void> second = arrayWrites.poll(5, TimeUnit.SECONDS);
        assertNotNull(second);
        assertFalse(refreshed.isComplete());
        complete(second);
        verify(variables, timeout(5000)).updateVariablesBatch(eq("async"), anyList());
        assertEquals(0, storage.writes.get());
        complete(scalarWrite);
        String json = storage.objects.poll(5, TimeUnit.SECONDS);
        assertNotNull(json);
        JsonNode output = new ObjectMapper().readTree(json);
        assertEquals("async", output.get("asyncId").asText());
        assertEquals(2, output.get("arrayMap").size());
        assertEquals(7, output.get("variableMap").get("scalar").get("value").asInt());
        assertFalse(serializedOnEventLoop.get());
        assertFalse(refreshed.isComplete());
        storage.release.countDown();
        assertTrue(await(refreshed).succeeded());
    }

    @Test
    public void failedWriteSettlesBeforeFailureAndStartsNoLaterWrites() throws Exception {
        inject(service, "variableValueDao", variables);
        inject(service, "storageDao", storage);
        Future<Void> write = Future.future();
        BlockingQueue<Boolean> started = new ArrayBlockingQueue<>(1);
        when(variables.storeArrayValueBatch(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(call -> {
                    started.add(true);
                    return write;
                });
        Future<Void> refreshed = service.refreshVariables("async", "vertex", resultWithArrays(), vertx);
        assertNotNull(started.poll(5, TimeUnit.SECONDS));
        assertFalse(refreshed.isComplete());
        IllegalStateException failure = new IllegalStateException("database write failed");
        vertx.runOnContext(ignored -> write.fail(failure));
        assertSame(failure, await(refreshed).cause());
        verify(variables, times(1)).storeArrayValueBatch(anyString(), anyString(), anyString(), anyMap());
        verify(variables, never()).updateVariablesBatch(anyString(), anyList());
        assertEquals(0, storage.writes.get());
    }

    @Test
    public void validatesEntireResultBeforeStartingWrites() throws Exception {
        inject(service, "variableValueDao", variables);
        inject(service, "storageDao", storage);
        Map<String, Object> result = resultWithArrays();
        result.put("binaryArrayFiles", Collections.singletonMap("array", "/worker/file"));
        assertTrue(await(service.refreshVariables("async", "vertex", result, vertx)).failed());
        verifyNoInteractions(variables);
        assertEquals(0, storage.writes.get());
    }

    @Test
    public void rejectsDuplicateVertexUntilOriginalAttemptSettles() throws Exception {
        OrchestratorAsyncTaskDao mappings = mock(OrchestratorAsyncTaskDao.class);
        inject(service, "orchestratorAsyncTaskDao", mappings);
        Future<Map<String, String>> lookup = Future.future();
        when(mappings.getMapping("async")).thenReturn(lookup);
        Future<Void> first = service.processNextElement("async", "vertex", vertx, false);
        assertTrue(service.processNextElement("async", "vertex", vertx, false).failed());
        verify(mappings, times(1)).getMapping("async");
        lookup.complete(Collections.emptyMap());
        assertTrue(first.succeeded());
        when(mappings.getMapping("async")).thenReturn(Future.succeededFuture(Collections.emptyMap()));
        assertTrue(service.processNextElement("async", "vertex", vertx, false).succeeded());
        verify(mappings, times(2)).getMapping("async");
    }

    @Test
    public void synchronousFailureReleasesVertexForRetry() throws Exception {
        OrchestratorAsyncTaskDao mappings = mock(OrchestratorAsyncTaskDao.class);
        inject(service, "orchestratorAsyncTaskDao", mappings);
        when(mappings.getMapping("async")).thenThrow(new IllegalStateException("lookup failed"));
        assertTrue(service.processNextElement("async", "vertex", vertx, false).failed());
        doReturn(Future.succeededFuture(Collections.emptyMap())).when(mappings).getMapping("async");
        assertTrue(service.processNextElement("async", "vertex", vertx, false).succeeded());
    }

    @Test
    public void dependencyFailureWaitsForOtherInFlightUpdates() throws Exception {
        DagElementDao dag = mock(DagElementDao.class);
        inject(service, "dagElementDao", dag);
        Future<Void> first = Future.future();
        Future<Void> second = Future.future();
        when(dag.removeDagElementDependency("parent", "first")).thenReturn(first);
        when(dag.removeDagElementDependency("parent", "second")).thenReturn(second);
        Method method = ProcessNextDagElementService.class.getDeclaredMethod("callNextElements",
                List.class, String.class, String.class, Vertx.class, Boolean.class);
        method.setAccessible(true);
        Object value = method.invoke(service, Arrays.asList("first", "second"), "async", "parent", vertx, false);
        assertTrue(value instanceof Future);
        Future<?> result = (Future<?>) value;
        first.fail("first dependency update failed");
        assertFalse(result.isComplete());
        second.fail("second dependency update failed");
        assertTrue(result.failed());
    }

    private static class RecordingStorage extends StorageDao {
        final BlockingQueue<String> objects = new ArrayBlockingQueue<>(1);
        final AtomicInteger writes = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(0);

        @Override
        protected void setObject(String id, String bucket, String object, int retries) throws Exception {
            assertEquals("async/dag_vertices/vertex", id);
            writes.incrementAndGet();
            objects.add(object);
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Storage write did not finish");
            }
        }

        @Override
        protected String getObject(String id, String bucket, int retries) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        protected void deleteObject(String id, String bucket, int retries) {
        }

        @Override
        protected void deletePrefix(String prefix, String bucket, int retries) {
            throw new UnsupportedOperationException("Not used");
        }
    }

    public static class ThreadCheckedValue {
        private final AtomicBoolean eventLoop;

        ThreadCheckedValue(AtomicBoolean eventLoop) {
            this.eventLoop = eventLoop;
        }

        public int getValue() {
            eventLoop.set(Context.isOnEventLoopThread());
            return 7;
        }
    }

    private Map<String, Object> resultWithArrays() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> arrays = new LinkedHashMap<>();
        arrays.put("array_a", Collections.singletonMap("0", 1));
        arrays.put("array_b", Collections.singletonMap("0", 2));
        result.put(Constants.arrayIndex, arrays);
        result.put("scalar", 3);
        return result;
    }

    private void complete(Future<Void> future) {
        vertx.runOnContext(ignored -> future.complete());
    }

    private static <T> AsyncResult<T> await(Future<T> future) throws Exception {
        CompletableFuture<AsyncResult<T>> result = new CompletableFuture<>();
        future.setHandler(result::complete);
        return result.get(5, TimeUnit.SECONDS);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

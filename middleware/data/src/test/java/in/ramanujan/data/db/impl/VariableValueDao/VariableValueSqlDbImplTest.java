package in.ramanujan.data.db.impl.VariableValueDao;

import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.ArrayMapping;
import in.ramanujan.db.layer.utils.QueryExecutor;
import io.vertx.core.Future;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class VariableValueSqlDbImplTest {
    private static class RecordingExecutor extends QueryExecutor {
        final List<List<Object>> batches = new ArrayList<>();
        final List<Future<List<Object>>> pending = new ArrayList<>();

        @Override
        public Future<List<Object>> execute(Object object, String index, QueryType type, List<Object>... batches) {
            assertEquals(QueryType.UPSERT, type);
            this.batches.add(batches[0]);
            Future<List<Object>> write = Future.future();
            pending.add(write);
            return write;
        }
    }

    @Test
    public void writesEveryIndexInBoundedSequentialBatches() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        VariableValueSqlDbImpl dao = dao(executor);
        Map<String, Object> values = values(2501);
        values.put("0", null);
        Future<Void> result = dao.storeArrayValueBatch("async", "array", "", values);

        assertEquals(1, executor.batches.size());
        assertEquals(1000, executor.batches.get(0).size());
        assertFalse(result.isComplete());
        executor.pending.get(0).complete(new ArrayList<>());
        assertEquals(2, executor.batches.size());
        assertEquals(1000, executor.batches.get(1).size());
        executor.pending.get(1).complete(new ArrayList<>());
        assertEquals(3, executor.batches.size());
        assertEquals(501, executor.batches.get(2).size());
        assertFalse(result.isComplete());
        executor.pending.get(2).complete(new ArrayList<>());
        assertTrue(result.succeeded());

        Set<String> indices = new HashSet<>();
        for (List<Object> batch : executor.batches) {
            for (Object row : batch) {
                ArrayMapping mapping = (ArrayMapping) row;
                assertEquals("async", mapping.getAsyncId());
                assertEquals("array", mapping.getArrayId());
                assertTrue(indices.add(mapping.getIndexStr()));
                Object expected = values.get(mapping.getIndexStr());
                assertEquals(expected == null ? null : expected.toString(), mapping.getObject());
            }
        }
        assertEquals(values.keySet(), indices);
    }

    @Test
    public void failedBatchStopsLaterWrites() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        Future<Void> result = dao(executor).storeArrayValueBatch("async", "array", "", values(2501));
        executor.pending.get(0).complete(new ArrayList<>());
        IllegalStateException failure = new IllegalStateException("write failed");
        executor.pending.get(1).fail(failure);
        assertSame(failure, result.cause());
        assertEquals(2, executor.batches.size());
    }

    @Test
    public void emptyArrayNeedsNoQuery() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        assertTrue(dao(executor).storeArrayValueBatch("async", "array", "", values(0)).succeeded());
        assertTrue(executor.batches.isEmpty());
    }

    private static VariableValueSqlDbImpl dao(QueryExecutor executor) throws Exception {
        VariableValueSqlDbImpl dao = new VariableValueSqlDbImpl();
        Field field = VariableValueSqlDbImpl.class.getDeclaredField("queryExecutor");
        field.setAccessible(true);
        field.set(dao, executor);
        return dao;
    }

    private static Map<String, Object> values(int size) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(String.valueOf(i), i);
        }
        return values;
    }
}

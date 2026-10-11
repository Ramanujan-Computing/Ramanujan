package in.ramanujan.db.layer.utils;

import org.junit.Test;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.VariableMapping;

import static org.junit.Assert.*;

public class QueryExecutorPoolSizeTest {
    @Test
    public void preservesDefaultWithoutOverride() {
        assertEquals(400, QueryExecutor.resolvePoolSize(null, 4));
        assertEquals(400, QueryExecutor.resolvePoolSize(" ", 4));
    }

    @Test
    public void honorsCloudRunPoolLimit() {
        assertEquals(8, QueryExecutor.resolvePoolSize("8", 4));
        assertEquals(8, QueryExecutor.resolvePoolSize(" 8 ", 4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZero() {
        QueryExecutor.resolvePoolSize("0", 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegative() {
        QueryExecutor.resolvePoolSize("-1", 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidSize() {
        QueryExecutor.resolvePoolSize("eight", 4);
    }

    @Test
    public void rejectsQueriesBeforeInitialization() throws Exception {
        io.vertx.core.Future<java.util.List<Object>> result =
                new QueryExecutor().execute(new VariableMapping(), null, QueryType.INSERT);
        assertTrue(result.failed());
        assertEquals("Database executor is not initialized", result.cause().getMessage());
    }

    @Test
    public void databaseConfigurationDoesNotExposePassword() {
        String config = new QueryExecutor.DBConfig("localhost", "test", "sensitive-test-value", "testdb").toString();
        assertFalse(config.contains("sensitive-test-value"));
        assertFalse(config.contains("password"));
    }
}

package in.ramanujan.db.layer.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}

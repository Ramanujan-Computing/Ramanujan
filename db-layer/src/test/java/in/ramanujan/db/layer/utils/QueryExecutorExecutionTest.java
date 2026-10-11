package in.ramanujan.db.layer.utils;

import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.VariableMapping;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class QueryExecutorExecutionTest {
    private final Vertx vertx = Vertx.vertx();

    @After
    public void closeVertx() throws Exception {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        vertx.close(handler -> closed.complete(null));
        closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void queryConstructionAndExecutionRunOnWorkerAndCloseBeforeSuccess() throws Exception {
        AtomicBoolean statementClosed = new AtomicBoolean();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        AtomicBoolean constructedOnWorker = new AtomicBoolean();
        QueryExecutor executor = executor(statementClosed, connectionClosed, false);
        VariableMapping row = new VariableMapping();
        List<Object> rows = new AbstractList<Object>() {
            @Override
            public Object get(int index) {
                constructedOnWorker.set(Context.isOnWorkerThread());
                return row;
            }

            @Override
            public int size() {
                return 1;
            }
        };
        CompletableFuture<AsyncResult<List<Object>>> result = new CompletableFuture<>();
        vertx.runOnContext(ignored -> {
            try {
                executor.execute(row, null, QueryType.INSERT, rows).setHandler(done -> {
                    assertTrue(statementClosed.get());
                    assertTrue(connectionClosed.get());
                    result.complete(done);
                });
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        assertTrue(result.get(5, TimeUnit.SECONDS).succeeded());
        assertTrue(constructedOnWorker.get());
    }

    @Test
    public void failedBatchClosesStatementAndConnectionBeforeReturningFailure() throws Exception {
        AtomicBoolean statementClosed = new AtomicBoolean();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        QueryExecutor executor = executor(statementClosed, connectionClosed, true);
        VariableMapping row = new VariableMapping();
        AsyncResult<List<Object>> result = await(executor.execute(row, null, QueryType.INSERT,
                java.util.Collections.singletonList(row)));
        assertTrue(result.failed());
        assertTrue(result.cause() instanceof SQLException);
        assertTrue(statementClosed.get());
        assertTrue(connectionClosed.get());
    }

    @Test
    public void malformedQueryFailsFutureRatherThanThrowingOnEventLoop() throws Exception {
        QueryExecutor executor = executor(new AtomicBoolean(), new AtomicBoolean(), false);
        assertTrue(await(executor.execute(new Object(), null, QueryType.INSERT)).failed());
    }

    private QueryExecutor executor(AtomicBoolean statementClosed, AtomicBoolean connectionClosed, boolean failBatch) {
        PreparedStatement statement = proxy(PreparedStatement.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "close":
                    statementClosed.set(true);
                    return null;
                case "executeBatch":
                    assertTrue(Context.isOnWorkerThread());
                    if (failBatch) {
                        throw new SQLException("batch failed");
                    }
                    return new int[]{1};
                case "setObject":
                case "addBatch":
                    return null;
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
        Connection connection = proxy(Connection.class, (instance, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                return statement;
            }
            if ("close".equals(method.getName())) {
                connectionClosed.set(true);
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        QueryExecutor executor = new QueryExecutor();
        executor.context = vertx.getOrCreateContext();
        executor.dataSource = proxy(DataSource.class, (instance, method, args) -> {
            if ("getConnection".equals(method.getName())) {
                return connection;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        return executor;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static <T> AsyncResult<T> await(Future<T> future) throws Exception {
        CompletableFuture<AsyncResult<T>> result = new CompletableFuture<>();
        future.setHandler(result::complete);
        return result.get(5, TimeUnit.SECONDS);
    }
}

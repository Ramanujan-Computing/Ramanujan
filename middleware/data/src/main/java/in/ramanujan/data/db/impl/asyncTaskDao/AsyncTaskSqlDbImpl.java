package in.ramanujan.data.db.impl.asyncTaskDao;

import in.ramanujan.data.db.dao.AsyncTaskDao;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.AsyncTaskMiddleware;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTaskFields;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AsyncTaskSqlDbImpl implements AsyncTaskDao {

    @Autowired
    private QueryExecutor queryExecutor;

    @Override
    public Future<Void> insert(AsyncTask asyncTask) {
        Future<Void> future = Future.future();
        try {
            queryExecutor.execute(asyncTask.getAsyncTaskMiddleware(), null, QueryType.INSERT)
                    .setHandler(new MonitoringHandler<>("asyncTaskAdd", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> update(String taskId, Map<String, Object> updateQuery) {
        Future<Void> future = Future.future();
        if(taskId == null) {
            return Future.failedFuture("No TaskId");
        }
        if(updateQuery == null) {
            return Future.failedFuture("No updateQuery");
        }
        try {
            AsyncTask asyncTask = new AsyncTask();
            asyncTask.setTaskId(taskId);
            for (String key : updateQuery.keySet()) {
                AsyncTaskFields.getAsyncTaskFields(key).triggerUpdate(asyncTask, updateQuery.get(key));
            }

            AsyncTaskMiddleware asyncTaskMiddleware = asyncTask.getAsyncTaskMiddleware();
            queryExecutor.execute(asyncTaskMiddleware, Keys.TASK_ID, QueryType.UPDATE)
                    .setHandler(new MonitoringHandler<>("asyncTaskUpdate", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<AsyncTask> getAsyncTask(String taskId) {
        Future<AsyncTask> future = Future.future();
        try {
            AsyncTaskMiddleware asyncTaskMiddleware = new AsyncTaskMiddleware();
            asyncTaskMiddleware.setTaskId(taskId);
            queryExecutor.execute(asyncTaskMiddleware, Keys.TASK_ID, QueryType.SELECT)
                    .setHandler(new MonitoringHandler<>("asyncTaskGet", handler -> {
                AsyncTask asyncTask = null;
                if(handler.succeeded()) {
                    List<Object> objects= handler.result();
                    for(Object obj : objects) {
                        asyncTask = new AsyncTask((AsyncTaskMiddleware) obj);
                    }
                    future.complete(asyncTask);
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

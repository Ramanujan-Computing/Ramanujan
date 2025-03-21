package in.ramanujan.data.db.dao;

import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public interface AsyncTaskDao {
    public Future<Void> insert(AsyncTask asyncTask);
    public Future<Void> update(String taskId, Map<String, Object> updateQuery);
    public Future<AsyncTask> getAsyncTask(String taskId);
}


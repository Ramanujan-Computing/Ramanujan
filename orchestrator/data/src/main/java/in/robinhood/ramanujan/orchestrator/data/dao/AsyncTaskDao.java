package in.robinhood.ramanujan.orchestrator.data.dao;

import in.robinhood.ramanujan.orchestrator.base.pojo.AsyncTask;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public interface AsyncTaskDao {
    public Future<Void> insert(AsyncTask asyncTask);
    public Future<Void> update(String uuid, Map<String, Object> updateQuery);
    public Future<AsyncTask> getAsyncTask(String uuid);
}

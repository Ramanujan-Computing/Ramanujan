package in.ramanujan.data.db.impl.asyncTaskDao;

import in.ramanujan.data.db.dao.AsyncTaskDao;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTaskFields;
import io.vertx.core.Future;

import java.util.HashMap;
import java.util.Map;


public class AsyncTaskHashMapImpl implements AsyncTaskDao {

    private Map<String, AsyncTask> asyncTaskMap;


    public void init() {
        asyncTaskMap = new HashMap<>();
    }

    @Override
    public Future<Void> insert(AsyncTask asyncTask) {
        if(asyncTask == null) {
            return Future.failedFuture("No asyncTask");
        }
        asyncTaskMap.put(asyncTask.getTaskId(), asyncTask);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> update(String taskId, Map<String, Object> updateQuery) {
        if(taskId == null) {
            return Future.failedFuture("No TaskId");
        }
        if(updateQuery == null) {
            return Future.failedFuture("No updateQuery");
        }
        try {
            AsyncTask asyncTask = asyncTaskMap.get(taskId);
            for (String key : updateQuery.keySet()) {
                AsyncTaskFields.getAsyncTaskFields(key).triggerUpdate(asyncTask, updateQuery.get(key));
            }
            return Future.succeededFuture();
        } catch (Exception e) {
            return  Future.failedFuture(e);
        }
    }

    @Override
    public Future<AsyncTask> getAsyncTask(String taskId) {
        if(taskId == null) {
            return Future.failedFuture("No taskId");
        }
        return Future.succeededFuture(asyncTaskMap.get(taskId));
    }
}

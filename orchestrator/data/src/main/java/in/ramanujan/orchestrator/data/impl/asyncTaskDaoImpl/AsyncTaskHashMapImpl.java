package in.ramanujan.orchestrator.data.impl.asyncTaskDaoImpl;

import in.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
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
        asyncTaskMap.put(asyncTask.getUuid(), asyncTask);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> update(String uuid, Map<String, Object> updateQuery) {
        try {
            AsyncTask asyncTask = asyncTaskMap.get(uuid);
            for (String key : updateQuery.keySet()) {
                AsyncTaskFields.getAsyncTaskFields(key).triggerUpdate(asyncTask, updateQuery.get(key));
            }
            return Future.succeededFuture();
        } catch (Exception e) {
            return  Future.failedFuture(e);
        }
    }

    @Override
    public Future<AsyncTask> getAsyncTask(String uuid) {
        return Future.succeededFuture(asyncTaskMap.get(uuid));
    }
}

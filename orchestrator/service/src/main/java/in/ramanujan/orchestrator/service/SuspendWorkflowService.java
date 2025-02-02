package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SuspendWorkflowService {

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private AsyncTaskHostMappingDao asyncTaskHostMappingDao;

    public Future<Void> suspend(String asyncId) {
        Future<Void> future = Future.future();
        try {
            asyncTaskDao.getAsyncTask(asyncId).setHandler(getAsyncTaskHandler -> {
                if(getAsyncTaskHandler.succeeded()) {
                    AsyncTask asyncTask = getAsyncTaskHandler.result();
                    asyncTask.setStatus(Status.FAILURE.getKeyName());
                    JsonObject updateQuery = new JsonObject()
                            .put(AsyncTaskFields.status.getFieldName(), Status.FAILURE.getKeyName());
                    asyncTaskDao.update(asyncTask.getUuid(), updateQuery.getMap()).setHandler(updateAsyncTaskHandler -> {
                        asyncTaskHostMappingDao.update(asyncTask.getHostAssigned(), asyncTask).setHandler(updateAsyncTaskHostMapping -> {
                            future.complete();
                        });
                    });
                } else {
                    future.fail(getAsyncTaskHandler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

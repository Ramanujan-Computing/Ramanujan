package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DebugInfoPushService {
    @Autowired
    private StorageDao storageDao;

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    public Future<Void> push(String asyncId, String debugvalues) {
        Future<Void> future = Future.future();
        try {
            storageDao.storeDebugResult(asyncId, debugvalues).setHandler(handler -> {
                if (handler.succeeded()) {
                    JsonObject updateQuery = new JsonObject()
                            .put(AsyncTaskFields.status.getFieldName(), Status.CHECKPOINT.getKeyName());
                    asyncTaskDao.update(asyncId, updateQuery.getMap()).setHandler(asyncTaskUpdateHandler -> {
                       if(asyncTaskUpdateHandler.succeeded()) {
                           future.complete();
                       } else {
                           future.fail(asyncTaskUpdateHandler.cause());
                       }
                    });
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

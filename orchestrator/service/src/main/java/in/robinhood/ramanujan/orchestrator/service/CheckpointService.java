package in.robinhood.ramanujan.orchestrator.service;

import in.robinhood.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.robinhood.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.robinhood.ramanujan.orchestrator.data.dao.StorageDao;
import in.robinhood.ramanujan.pojo.checkpoint.Checkpoint;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckpointService {

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private StorageDao storageDao;

    public Future<Void> applyCheckpoint(Checkpoint checkpoint, String asyncTaskId) throws Exception {
        Future<Void> future = Future.future();
        storageDao.storeCheckpoint(asyncTaskId, checkpoint).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}

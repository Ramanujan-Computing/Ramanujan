package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrchestratorCheckpointService {

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

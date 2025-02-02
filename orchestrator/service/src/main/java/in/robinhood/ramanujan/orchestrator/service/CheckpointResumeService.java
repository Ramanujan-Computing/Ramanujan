package in.robinhood.ramanujan.orchestrator.service;

import in.robinhood.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.robinhood.ramanujan.orchestrator.base.pojo.CheckpointResumePayload;
import in.robinhood.ramanujan.orchestrator.data.dao.HostsDao;
import in.robinhood.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckpointResumeService {

    @Autowired
    private StorageDao storageDao;

    @Autowired
    private HostsDao hostsDao;

    /**
     * <pre>
     * 1. add resume checkpoint on storage.
     * 2. select machine for processing.
     * </pre>
     */
    public Future<Void> resumeCheckpoint(String asyncId, CheckpointResumePayload checkpointResumePayload) {
        Future<Void> future = Future.future();
        storageDao.storeBreakpoints(asyncId, checkpointResumePayload).setHandler(storeBreakpointHandler -> {
           if(storeBreakpointHandler.succeeded()) {
               AsyncTask asyncTask = new AsyncTask();
               asyncTask.setUuid(asyncId); //TODO: asyncTask obj is not necessarily required. To refactor this in the hostDao.getMachine().
               hostsDao.getMachine(asyncTask, true).setHandler(getMachineHandler -> {
                  if(getMachineHandler.succeeded()) {
                      future.complete();
                  } else {
                      future.fail(getMachineHandler.cause());
                  }
               });
           } else {
               future.fail(storeBreakpointHandler.cause());
           }
        });
        return future;
    }
}

package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.HostsDao;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;



@Component
public class OpenPingService {

    @Autowired
    private HostsDao hostsDao;

    @Autowired
    private StorageDao storageDao;

    public Future<AsyncTask> storePing(String hostId) {
        Future<AsyncTask> future = Future.future();
        hostsDao.putMachineForComputation(hostId).setHandler(handler -> {
           if(handler.succeeded()) {
               AsyncTask task = handler.result();
               if (task == null || task.getRuleEngineInput() == null) {
                   future.complete(task);
                   return;
               }
               storageDao.createResultUploadTarget(task.getUuid(), hostId).setHandler(upload -> {
                   if (upload.succeeded()) {
                       task.setResultUpload(upload.result());
                       future.complete(task);
                   } else {
                       future.fail(upload.cause());
                   }
               });
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}

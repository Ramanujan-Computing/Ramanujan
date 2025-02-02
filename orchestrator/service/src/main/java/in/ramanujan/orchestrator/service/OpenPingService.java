package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.HostsDao;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;



@Component
public class OpenPingService {

    @Autowired
    private HostsDao hostsDao;

    public Future<AsyncTask> storePing(String hostId) {
        Future<AsyncTask> future = Future.future();
        hostsDao.putMachineForComputation(hostId).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete(handler.result());
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}

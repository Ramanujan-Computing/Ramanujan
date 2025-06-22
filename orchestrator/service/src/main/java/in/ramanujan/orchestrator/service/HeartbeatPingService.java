package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.base.pojo.HeartBeat;
import in.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import in.ramanujan.orchestrator.data.dao.HeartBeatDao;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class HeartbeatPingService {

    @Autowired
    private HeartBeatDao heartBeatDao;

    @Autowired
    private AsyncTaskHostMappingDao asyncTaskHostMappingDao;

    public Future<Boolean> pingHeartbeat(String asyncId, String hostId) {
        Future<Boolean> future = Future.future();
        HeartBeat  heartBeat = new HeartBeat(asyncId, hostId, null, new Date().toInstant().toEpochMilli());
        heartBeatDao.updateHeartBeat(heartBeat).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete(true);
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}

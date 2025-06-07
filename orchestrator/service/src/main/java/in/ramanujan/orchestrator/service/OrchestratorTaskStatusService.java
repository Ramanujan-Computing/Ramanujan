package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.DateTimeUtils;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.base.pojo.HeartBeat;
import in.ramanujan.orchestrator.data.dao.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;


@Component
public class OrchestratorTaskStatusService {

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private HeartBeatDao heartBeatDao;

    @Autowired
    private HostsDao hostsDao;

    @Autowired
    private StorageDao storageDao;

    @Autowired
    private AsyncTaskHostMappingDao asyncTaskHostMappingDao;

    private Logger logger= LoggerFactory.getLogger(OrchestratorTaskStatusService.class);

    public Future<AsyncTask> getAsyncTaskStatus(String asyncTaskId) {
        Future<AsyncTask> future = Future.future();
        Future<AsyncTask> getAsyncTaskFut = asyncTaskDao.getAsyncTask(asyncTaskId);
        Future<String> getHostInfo = asyncTaskHostMappingDao.getHostForTask(asyncTaskId);
        CompositeFuture.all(getAsyncTaskFut, getHostInfo).setHandler(handler -> {
            if(handler.succeeded()) {
                AsyncTask asyncTask = getAsyncTaskFut.result();
                if(Status.SUCCESS.getKeyName().equalsIgnoreCase(asyncTask.getStatus()))  {
                    future.complete(asyncTask);
                    return;
                }
                if(getHostInfo.result() == null)  {
                    getMachine(asyncTask, future);
                    return;
                }
                asyncTask.setHostAssigned(getHostInfo.result());
                heartBeatDao.getLastHeartBeat(asyncTask.getHostAssigned()).setHandler(heartBeatHandler -> {
                   if(heartBeatHandler.succeeded()) {
                       HeartBeat heartBeat = heartBeatHandler.result();
                       if (heartBeat == null || (new Date().toInstant().toEpochMilli() - heartBeat.getHeartBeatTimeEpoch()) > DateTimeUtils.maxHeartBeatDiff) {
                           //current host has become non-responsive
                           //need to get a new host and restart computation
                           getNewHostAndStartComputation(asyncTask, future);
                       } else {
                           //current host is up and running, need to keep pinging
                           logger.info("Computation still running for " + asyncTask.getUuid());
                           future.complete(asyncTask);
                       }
                   } else {
                       future.complete(asyncTask);
                   }
                });
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    private void getNewHostAndStartComputation(AsyncTask asyncTask, Future<AsyncTask> future) {
        asyncTaskHostMappingDao.deleteTask(asyncTask.getUuid()).setHandler(deleteTaskHandler -> {
            if(deleteTaskHandler.succeeded()) {
                getMachine(asyncTask, future);
            } else {
                future.fail(deleteTaskHandler.cause());
            }
        });
    }

    private void getMachine(AsyncTask asyncTask, Future<AsyncTask> future) {
        hostsDao.getMachine(asyncTask, true).setHandler(hostMachineGetter -> {
            if(hostMachineGetter.succeeded()) {
                logger.info(asyncTask.getUuid() + " got a new machine " + hostMachineGetter.result());
                asyncTask.setHostAssigned(hostMachineGetter.result());
                future.complete(asyncTask);
            } else {
                future.complete(asyncTask);
            }
        });
    }
}

package in.ramanujan.orchestrator.data.impl.hostDaoImpl;

import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import in.ramanujan.orchestrator.data.dao.HeartBeatDao;
import in.ramanujan.orchestrator.data.external.OrchestratorApiCaller;
import in.ramanujan.orchestrator.data.dao.HostsDao;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class HostDaoStackImpl implements HostsDao {

    Logger logger= LoggerFactory.getLogger(HostDaoStackImpl.class);

    private Stack<String> hostStack;

    private Set<String> isHostInStack;

    @Autowired
    private AsyncTaskHostMappingDao asyncTaskHostMappingDao;

    @Autowired
    private HeartBeatDao heartBeatDao;

    @Autowired
    private OrchestratorApiCaller orchestratorApiCaller;

    @Autowired
    private StorageDao storageDao;

    @PostConstruct
    public void init() {
        hostStack = new Stack<>();
        isHostInStack = new HashSet<>();
    }

    @Override
    public synchronized Future<String> getMachine(AsyncTask asyncTask, Boolean resumeComputation) {
        if(hostStack.empty()) {
            logger.error(asyncTask.getUuid() + " no machine available for computation");
            return Future.succeededFuture("No Machine available");
        } else {
            String hostId = hostStack.pop();
            Future<String> future = Future.future();

            logger.info(asyncTask.getUuid() + " has got probable machine " + hostId);
            //This table has a uniqueId on hostId. This will fail if more than one process tries to take it.
            asyncTaskHostMappingDao.createMapping(asyncTask, hostId, resumeComputation).setHandler(mappingCreateHandler -> {
               if(mappingCreateHandler.succeeded()) {
                   logger.info(asyncTask.getUuid() + " has been assigned machine " + hostId);
                   future.complete(hostId);
               } else {
                   isHostInStack.remove(hostId);
                   logger.error(asyncTask.getUuid() + " has NOT been assigned machine " + hostId, mappingCreateHandler.cause());
                   //on taskStatus, if host is null, reassign will happen.
                   future.complete();
               }
            });

            return future;
        }
    }

    @Override
    public Future<AsyncTask> putMachineForComputation(String hostId) {
        /*
        * Check if any asynctask is being processed by the hostId
        * Check if there is an entry in availableHost with proper timelimit and status as ENGAGED.
        * If not, it can be added to the stack.
        * Also, check if the stack size is above 10^4. If yes, then send this request to some other host and start removing stack items.
        * */
        Future<AsyncTask> future = Future.future();
        if(hostStack.size() >= 10000) {
            callOrchestratorApiForMachineAddition(hostId, future);
            removeOldHosts();
            return future;
        }
        //logger.info("stack size: " + hostStack.size());
        asyncTaskHostMappingDao.getMapping(hostId).setHandler(handler -> {
           if(handler.succeeded()) {
               if(handler.result() == null || Status.FAILURE.getKeyName().equalsIgnoreCase(handler.result().getStatus())) {
                   //logger.info("No asyncTask for machine " + hostId + ", adding in stack");
                   addInStack(hostId, future);
               } else {
                   logger.info("Machine " + hostId + " is assigned " + handler.result().getUuid());
                   isHostInStack.remove(hostId);
                   AsyncTask asyncTask = handler.result();
                   if (asyncTask != null) {
                       CompositeFuture.all(getAsyncTaskRuleEngineInputStorageDao(asyncTask),
                               getCheckpointData(asyncTask), getDebugBreakpoints(asyncTask)).setHandler(compositeHandler -> {
                           if (compositeHandler.succeeded()) {
                               future.complete(asyncTask);
                           } else {
                               logger.error("machine " + hostId + " not able to get asyncTask data due to ", compositeHandler.cause());
                               future.fail(compositeHandler.cause());
                           }
                       });
                   } else {
                       future.complete(handler.result());
                   }
               }
           } else {
               logger.error("machine " + hostId + " not able to get current asyncTask mapping due to ", handler.cause());
               future.fail(handler.cause());
           }
        });
        return future;
    }

    private Future<Void> getDebugBreakpoints(AsyncTask asyncTask) {
        Future<Void> future = Future.future();
        storageDao.getBreakpoints(asyncTask.getUuid()).setHandler(handler -> {
           if(handler.succeeded()) {
               if(handler.result() != null) {
                   asyncTask.setBreakpoints(handler.result().getLines());
               } else {
                   asyncTask.setBreakpoints(new ArrayList<>());
               }
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }

    private Future<Void> getCheckpointData(AsyncTask asyncTask) {
        Future<Void> future = Future.future();
        if(asyncTask.getCheckpoint() == null) {
            return Future.succeededFuture();
        }
        try {
            storageDao.getCheckpoint(asyncTask.getUuid()).setHandler(handler -> {
                if(handler.succeeded()) {
                    asyncTask.setCheckpoint(handler.result());
                } else {
                    asyncTask.setCheckpoint(null);
                }
                future.complete();
            });
        } catch (Exception e) {
            asyncTask.setCheckpoint(null);
        }
        return future;
    }

    private Future<Void> getAsyncTaskRuleEngineInputStorageDao(AsyncTask asyncTask) {
        Future<Void> future = Future.future();
        try {
            storageDao.getAsyncTaskRuleEngineInput(asyncTask.getUuid()).setHandler(ruleEngineInputHandler -> {
               if(ruleEngineInputHandler.succeeded()) {
                   asyncTask.setRuleEngineInput(ruleEngineInputHandler.result());
                   future.complete();
               } else {
                   future.fail(ruleEngineInputHandler.cause());
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    private void removeOldHosts() {
        hostStack = new Stack<>();
        isHostInStack = new HashSet<>();
    }

    private void callOrchestratorApiForMachineAddition(String hostId, Future<AsyncTask> future) {
        orchestratorApiCaller.callOpenPingApiWithRetry(hostId, 3).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
    }

    private void addInStack(String hostId, Future<AsyncTask> future) {
        if(!isHostInStack.contains(hostId)) {
            hostStack.push(hostId);
            isHostInStack.add(hostId);
            logger.info("pushed in stack");
        }
        future.complete(null);
    }
}

package in.ramanujan.middleware.service;

import com.sun.management.OperatingSystemMXBean;
import in.ramanujan.data.db.dao.*;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.data.KafkaManagerApiCaller;
import in.ramanujan.data.OrchestrationApiCaller;
import in.ramanujan.middleware.base.enums.Status;
import in.ramanujan.middleware.base.pojo.DeviceExecStatus;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import in.ramanujan.utils.Constants;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProcessNextDagElementService {

    @Autowired
    private OrchestratorAsyncTaskDao orchestratorAsyncTaskDao;

    @Autowired
    private OrchestrationApiCaller orchestrationApiCaller;

    @Autowired
    private DagElementDao dagElementDao;

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private RunService runService;

    @Autowired
    private KafkaManagerApiCaller kafkaManagerApiCaller;

    @Autowired
    private VariableValueDao variableValueDao;

    @Autowired
    private OrchestratorCallLockerDao orchestratorCallLockerDao;

    private Logger logger = LoggerFactory.getLogger(ProcessNextDagElementService.class);

    AtomicInteger countConcurrentRequest = new AtomicInteger(0);

    int maxProcessing = 100 * Runtime.getRuntime().availableProcessors();

    private double lastCpuCalcTime = 0d;
    private double lastCpuVal = 0d;

    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public Future<Void> processNextElement(final String asyncId, final String dagElementId, Vertx vertx, Boolean toBeDebugged) throws Exception {
        Future<Void> future = Future.future();
        if(System.currentTimeMillis() - lastCpuCalcTime > 5000) {
            lastCpuVal = osBean.getProcessCpuLoad();
            lastCpuCalcTime = System.currentTimeMillis();
        }
        if(lastCpuVal > 0.5) {
            logger.error("High CPU usage detected: " + lastCpuVal);
            return Future.failedFuture("High CPU usage: " + lastCpuVal);
        }
        logger.info("Current CPU usage: " + lastCpuVal);
        if (countConcurrentRequest.incrementAndGet() > maxProcessing) {
            logger.warn("Too many concurrent requests, returning without processing: " + asyncId);
            future.fail("Too many requests");
            return future;
        }
        logger.info("Processing next element for asyncId: {}, dagElementId: {}, toBeDebugged: {}", asyncId, dagElementId, toBeDebugged);
        orchestratorAsyncTaskDao.getMapping(asyncId).setHandler(handler -> {
            if (handler.result() == null) {
                logger.warn("No mapping found for asyncId: " + asyncId);
                countConcurrentRequest.decrementAndGet();
                future.complete();
                return;
            }
            final Map<String, String> map = handler.result();
            final String orchId = map.get(dagElementId);
            if (orchId == null) {
                countConcurrentRequest.decrementAndGet();
                future.complete();
                return;
            }
            Long statusApiStart = new Date().toInstant().toEpochMilli();
            orchestrationApiCaller.callStatusApi(asyncId, orchId, dagElementId).setHandler(statusApiHandler -> {
                logger.info("status API: " + getTimeElapsed(statusApiStart));
                if (statusApiHandler.succeeded()) {
                    if(statusApiHandler.result() == null) {
                        kafkaManagerApiCaller.callEventApi(asyncId, dagElementId, toBeDebugged).setHandler(retryHandler -> {
                            countConcurrentRequest.decrementAndGet();
                            future.complete();
                        });
                    } else {
                        DeviceExecStatus deviceExecStatus = statusApiHandler.result();
                        if(deviceExecStatus.getStatus() == Status.CHECKPOINT) {
                            asyncTaskDao.update(asyncId, new HashMap<String, Object>() {
                                {
                                    put("taskStatus", AsyncTask.TaskStatus.CHECKPOINT);
                                    put("result", null);
                                }
                            }).setHandler(updateHandler -> {
                                future.complete();
                            });
                            return;
                        }
                        Long refreshVarStart = new Date().toInstant().toEpochMilli();
                        refreshVariables(asyncId, dagElementId, deviceExecStatus.getData())
                                .setHandler(new MonitoringHandler<>("refreshVariables", refreshVariablesHandler -> {
                            logger.info("refreshVar : " + getTimeElapsed(refreshVarStart));
                            if(refreshVariablesHandler.succeeded()) {
                                Long removeStart = new Date().toInstant().toEpochMilli();
                                dagElementDao.removeDagElementAsyncIdMap(asyncId, dagElementId) //redundant in case of sql.
                                        .setHandler(new MonitoringHandler<>("removeDagElementIdFromAsyncTask", removeDagElementHandler -> {
                                    logger.info("remove time: " + getTimeElapsed(removeStart));
                                    Long nextIdFetchStart = new Date().toInstant().toEpochMilli();
                                    dagElementDao.getNextId(dagElementId, false/*so that if nextElem was not able to get started, we dont stop here in the next run.*/).
                                            setHandler(new MonitoringHandler<>("getNextDagElementId", getNextDagElements -> {
                                        logger.info("nextId fetch: " + getTimeElapsed(nextIdFetchStart));
                                        if(getNextDagElements.succeeded()) {
                                            if(getNextDagElements.result().size() == 0) {
                                                handleNoNextDagElement(asyncId).setHandler(noNextDagElementHandler -> {
                                                    countConcurrentRequest.decrementAndGet();
                                                    future.complete();
                                                });
                                            } else {
                                                callNextElements(getNextDagElements.result(), asyncId, dagElementId, vertx, toBeDebugged)
                                                        .setHandler(nextElementCallHandler -> {
                                                    countConcurrentRequest.decrementAndGet();
                                                    if(nextElementCallHandler.succeeded()) {
                                                        future.complete();
                                                    } else {
                                                        logger.error("Failed to call next elements", nextElementCallHandler.cause());
                                                        future.fail(nextElementCallHandler.cause());
                                                    }
                                                });
                                            }
                                        } else {
                                            countConcurrentRequest.decrementAndGet();
                                            future.fail(getNextDagElements.cause());
                                        }
                                    }));
                                }));
                            } else {
                                countConcurrentRequest.decrementAndGet();
                                future.fail(refreshVariablesHandler.cause());
                            }
                        }));

                    }
                } else {
                    asyncTaskDao.update(asyncId, new HashMap<String, Object>() {
                        {
                            put("taskStatus", AsyncTask.TaskStatus.FAILED);
                            put("result", handler.cause());
                        }
                    });
                    countConcurrentRequest.decrementAndGet();
                    future.complete();
                }
            });
        });
        return future;
    }

    private static long getTimeElapsed(Long start) {
        return new Date().toInstant().toEpochMilli() - start;
    }

    private Future<Void> handleNoNextDagElement(String asyncId) {
        Future<Void> future = Future.future();
        dagElementDao.isAsyncTaskDone(asyncId).setHandler(isAsyncTaskDoneHandler -> {
            if(isAsyncTaskDoneHandler.result()) {
                //terminate the asyncTask
                asyncTaskDao.update(asyncId, new HashMap<String, Object>() {
                    {
                        put("taskStatus", AsyncTask.TaskStatus.SUCCESS);
                        put("result", null);
                    }
                }).setHandler(updateHandler -> {
                    future.complete();
                });
            } else {
                //dont' do anything
                future.complete();
            }
        });
        return future;
    }

    private Future<Void> refreshVariables(String asyncId, String dagElementId, Map<String, Object> result) {
        Future<Void> future = Future.future();
        List<Future> updateVariableFutures = new ArrayList<>();
        // Collect variables for batch update
        List<in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable> variablesToUpdate = new ArrayList<>();
        for(String key : result.keySet()) {
            if(Constants.arrayIndex.equalsIgnoreCase(key)) {
                Map<String, Map<String, Object>> arrayMap = (Map) result.get(key);
                for(String arrayId : arrayMap.keySet()) {
                    if(arrayId.contains("func")) {
                        continue;
                    }
                    Map<String, Object> arrayIndexMap = arrayMap.get(arrayId);
                    // Use batch update for all indexes of this arrayId. Pass empty string for arrayName (or replace if you have the name)
                    updateVariableFutures.add(variableValueDao.storeArrayValueBatch(asyncId, arrayId, "", arrayIndexMap));
                }
            } else {
                if(key.contains("func")) {
                    continue;
                }
                // Create a Variable object for batch update
                in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable variable = new in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable();
                variable.setId(key);
                variable.setValue(result.get(key));
                variablesToUpdate.add(variable);
            }
        }
        if (!variablesToUpdate.isEmpty()) {
            updateVariableFutures.add(variableValueDao.updateVariablesBatch(asyncId, variablesToUpdate));
        }
        CompositeFuture.all(updateVariableFutures).setHandler(updateVariableFuturesHandler -> {
            if(updateVariableFuturesHandler.succeeded()) {
                future.complete();
            } else {
                logger.error("Variable update failed", updateVariableFuturesHandler.cause());
                future.fail(updateVariableFuturesHandler.cause());
            }
        });
        return future;
    }

    private void updateArrayOnIndex(Object[] indexes, Future<Void> arrayIndexFuture, String arrayId, int index, Map<String, Object> arrayIndexMap, String asyncId) {
        if(index == indexes.length) {
            arrayIndexFuture.complete();
            return;
        }
        variableValueDao.storeArrayValue(asyncId, arrayId, null, (String)indexes[index], arrayIndexMap.get(indexes[index])).setHandler(handler -> {
           if(handler.succeeded()) {
               updateArrayOnIndex(indexes, arrayIndexFuture, arrayId, index + 1, arrayIndexMap, asyncId);
           } else {
               arrayIndexFuture.fail(handler.cause());
           }
        });
    }


    private Future<Void> callNextElements(List<String> nextDagElementIds, String asyncId, String dagElementId,
                                          Vertx vertx, Boolean toBeDebugged) {
        Future<Void> callNextElementFuture = Future.future();
        List<Future> futures = new ArrayList<>();
        for(String nextDagElementId : nextDagElementIds) {
            Future<Void> future = Future.future();
            futures.add(future);
            Long dependencyRemoveStart = new Date().toInstant().toEpochMilli();
            dagElementDao.removeDagElementDependency(dagElementId, nextDagElementId)
                    .setHandler(new MonitoringHandler<>("removeDagElementDependency", removedHandler -> {
                logger.info("dependencyRemove: " + getTimeElapsed(dependencyRemoveStart));
                callNextElementIfIsNotDependent(asyncId, dagElementId, vertx, nextDagElementId, future, toBeDebugged);
            }));
        }
        CompositeFuture.all(futures).setHandler(handler -> {
            callNextElementFuture.complete();
        });
        return callNextElementFuture;
    }

    private void callNextElementIfIsNotDependent(String asyncId, String dagElementId, Vertx vertx, String nextDagElementId,
                                                 Future<Void> future, Boolean toBeDebugged) {
        Long noDependencyCheckStart = new Date().toInstant().toEpochMilli();
        dagElementDao.isDagElementStillDependent(nextDagElementId)
                .setHandler(new MonitoringHandler<>("isDagElementStillDependent", dependenceHandler -> {
            logger.info("noDependencyCheck: " + getTimeElapsed(noDependencyCheckStart));
            if (!dependenceHandler.result()) {
                Long lockCheckStart = new Date().toInstant().toEpochMilli();
                attainLock(nextDagElementId).setHandler(new MonitoringHandler<>("attainLockOnDagElementId", attainer -> {
                    logger.info("lockCheck: " + getTimeElapsed(lockCheckStart));
                    if(attainer.result()) {
                        logger.info(dagElementId + " calling for " + nextDagElementId);
                        Long orchestratorCallStart = new Date().toInstant().toEpochMilli();
                        runService.runDagElementId(asyncId, nextDagElementId, vertx, toBeDebugged)
                                .setHandler(new MonitoringHandler<>("runDagElementId", runHandler -> {
                            logger.info("orchestratorAPI: " + getTimeElapsed(orchestratorCallStart));
                            if(!runHandler.succeeded()) {
                                logger.error("Failed to run dag element id: " + nextDagElementId, runHandler.cause());
                                future.fail(runHandler.cause());
                                return;
                            }
                            kafkaManagerApiCaller.callEventApi(asyncId, nextDagElementId, toBeDebugged).setHandler(kafkaMgrCall -> {
                                future.complete();
                            });
                        }));
                    } else {
                        future.complete();
                    }
                }));
            } else {
                future.complete();
            }
        }));
    }

    private Future<Boolean> attainLock(final String dagElementId) {
        return Future.succeededFuture(true);
//        final String uuid = UUID.randomUUID().toString();
//        Future<Boolean> future = Future.future();
//        orchestratorCallLockerDao.insertLocker(uuid, dagElementId, new Date().toInstant().toEpochMilli()).setHandler(insertHandler -> {
//            orchestratorCallLockerDao.attainedLock(uuid, dagElementId).setHandler(attainer -> {
//               future.complete(attainer.result());
//            });
//        });
//        return future;
    }

}

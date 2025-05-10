package in.ramanujan.middleware.service;

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

import java.util.*;

public class ProcessNextDagElementService {

    public OrchestratorAsyncTaskDao orchestratorAsyncTaskDao;
    public OrchestrationApiCaller orchestrationApiCaller;
    public DagElementDao dagElementDao;
    public AsyncTaskDao asyncTaskDao;
    public RunService runService;
    public KafkaManagerApiCaller kafkaManagerApiCaller;
    public VariableValueDao variableValueDao;
    public OrchestratorCallLockerDao orchestratorCallLockerDao;

    private Logger logger = LoggerFactory.getLogger(ProcessNextDagElementService.class);

    public Future<DeviceExecStatus> processNextElement(final String asyncId, final String dagElementId, Vertx vertx, Boolean toBeDebugged) throws Exception {
        Future<DeviceExecStatus> future = Future.future();
        orchestratorAsyncTaskDao.getMapping(asyncId).setHandler(handler -> {
            if (handler.result() == null) {
                future.complete();
                return;
            }
            final Map<String, String> map = handler.result();
            final String orchId = map.get(dagElementId);
            if (orchId == null) {
                future.complete();
                return;
            }
            Long statusApiStart = new Date().toInstant().toEpochMilli();
            orchestrationApiCaller.callStatusApi(asyncId, orchId, dagElementId).setHandler(statusApiHandler -> {
                logger.info("status API: " + getTimeElapsed(statusApiStart));
                if (statusApiHandler.succeeded()) {
                    if(statusApiHandler.result() == null) {
                        kafkaManagerApiCaller.callEventApi(asyncId, dagElementId, toBeDebugged).setHandler(retryHandler -> {
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
                                dagElementDao.removeDagElementAsyncIdMap(asyncId, dagElementId)
                                        .setHandler(new MonitoringHandler<>("removeDagElementIdFromAsyncTask", removeDagElementHandler -> {
                                    logger.info("remove time: " + getTimeElapsed(removeStart));
                                    Long nextIdFetchStart = new Date().toInstant().toEpochMilli();
                                    dagElementDao.getNextId(dagElementId, true).
                                            setHandler(new MonitoringHandler<>("getNextDagElementId", getNextDagElements -> {
                                        logger.info("nextId fetch: " + getTimeElapsed(nextIdFetchStart));
                                        if(getNextDagElements.succeeded()) {
                                            if(getNextDagElements.result().size() == 0) {
                                                handleNoNextDagElement(asyncId).setHandler(noNextDagElementHandler -> {
                                                    future.complete();
                                                });
                                            } else {
                                                callNextElements(getNextDagElements.result(), asyncId, dagElementId, vertx, toBeDebugged)
                                                        .setHandler(nextElementCallHandler -> {
                                                    future.complete();
                                                });
                                            }
                                        } else {
                                            future.fail(getNextDagElements.cause());
                                        }
                                    }));
                                }));
                            } else {
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
        for(String key : result.keySet()) {
            if(Constants.arrayIndex.equalsIgnoreCase(key)) {
                Map<String, Map<String, Object>> arrayMap = (Map) result.get(key);
                for(String arrayId : arrayMap.keySet()) {
                    Map<String, Object> arrayIndexMap = arrayMap.get(arrayId);
//                    updateVariableFutures.add(variableValueDao.storeArrayValueBatch(asyncId, arrayId, arrayIndexMap));
//                    Future<Void> arrayIndexFuture = Future.future();
//                    updateVariableFutures.add(arrayIndexFuture);
//                    updateArrayOnIndex(arrayIndexMap.keySet().toArray(), arrayIndexFuture, arrayId, 0, arrayIndexMap, asyncId);
                    for(String index : arrayIndexMap.keySet()) {
                        Future<Void> updateVariableFuture = Future.future();
                        updateVariableFutures.add(updateVariableFuture);
                        variableValueDao.storeArrayValue(asyncId, arrayId, index, arrayIndexMap.get(index))
                                .setHandler(arrayIndexUpdateHandler -> {
                                    if(arrayIndexUpdateHandler.succeeded()) {
                                        updateVariableFuture.complete();
                                    } else {
                                        updateVariableFuture.fail(updateVariableFuture.cause());
                                    }
                        });
                    }
                }
            } else {
                Future<Void> updateVariableFuture = Future.future();
                updateVariableFutures.add(updateVariableFuture);
                variableValueDao.storeVariableValue(asyncId, key, result.get(key)).setHandler(handler -> {
                    if(handler.succeeded()) {
                        updateVariableFuture.complete();
                    } else {
                        updateVariableFuture.fail(handler.cause());
                    }
                });
            }
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
        variableValueDao.storeArrayValue(asyncId, arrayId, (String)indexes[index], arrayIndexMap.get(indexes[index])).setHandler(handler -> {
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
        final String uuid = UUID.randomUUID().toString();
        Future<Boolean> future = Future.future();
        orchestratorCallLockerDao.insertLocker(uuid, dagElementId, new Date().toInstant().toEpochMilli()).setHandler(insertHandler -> {
            orchestratorCallLockerDao.attainedLock(uuid, dagElementId).setHandler(attainer -> {
               future.complete(attainer.result());
            });
        });
        return future;
    }

}

package in.ramanujan.middleware.service;

import com.sun.management.OperatingSystemMXBean;
import in.ramanujan.data.db.dao.*;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.data.KafkaManagerApiCaller;
import in.ramanujan.data.OrchestrationApiCaller;
import in.ramanujan.middleware.base.enums.Status;
import in.ramanujan.middleware.base.pojo.DeviceExecStatus;
import in.ramanujan.middleware.base.pojo.DagVertexOutput;
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
import java.util.concurrent.ConcurrentHashMap;

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

    @Autowired
    private StorageDao storageDao;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final Set<List<String>> processingVertices = ConcurrentHashMap.newKeySet();

    private Logger logger = LoggerFactory.getLogger(ProcessNextDagElementService.class);

    AtomicInteger countConcurrentRequest = new AtomicInteger(0);

    int maxProcessing = 100 * Runtime.getRuntime().availableProcessors();

    private double lastCpuCalcTime = 0d;
    private double lastCpuVal = 0d;

    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public Future<Void> processNextElement(final String asyncId, final String dagElementId, Vertx vertx, Boolean toBeDebugged) throws Exception {
        List<String> key = Arrays.asList(asyncId, dagElementId);
        if (!processingVertices.add(key)) {
            logger.warn("DAG vertex is already being processed: " + asyncId + "/" + dagElementId);
            return Future.failedFuture("DAG vertex is already being processed: " + asyncId + "/" + dagElementId);
        }
        Future<Void> future = Future.future();
        try {
            processNextElementInternal(asyncId, dagElementId, vertx, toBeDebugged).setHandler(handler -> {
                processingVertices.remove(key);
                if (handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            processingVertices.remove(key);
            logger.error("Failed to process DAG vertex " + asyncId + "/" + dagElementId, e);
            future.fail(e);
        }
        return future;
    }

    private Future<Void> processNextElementInternal(final String asyncId, final String dagElementId, Vertx vertx, Boolean toBeDebugged) throws Exception {
        Future<Void> future = Future.future();

        logger.info("Processing next element for asyncId: {}, dagElementId: {}, toBeDebugged: {}", asyncId, dagElementId, toBeDebugged);
        orchestratorAsyncTaskDao.getMapping(asyncId).setHandler(handler -> {
            if (handler.failed()) {
                future.fail(handler.cause());
                return;
            }
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
                //logger.info("status API: " + getTimeElapsed(statusApiStart));
                if (statusApiHandler.succeeded()) {
                    if(statusApiHandler.result() == null) {
                        future.fail("Status API returned null for asyncId: " + asyncId);
                    } else {
                        DeviceExecStatus deviceExecStatus = statusApiHandler.result();
                        if (deviceExecStatus.getStatus() == Status.PROCESSING) {
                            kafkaManagerApiCaller.callEventApi(asyncId, dagElementId, toBeDebugged).setHandler(requeueHandler -> {
                                countConcurrentRequest.decrementAndGet();
                                if (requeueHandler.succeeded()) {
                                    future.complete();
                                } else {
                                    future.fail(requeueHandler.cause());
                                }
                            });
                            return;
                        }
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
                        refreshVariables(asyncId, dagElementId, deviceExecStatus.getData(), vertx)
                                .setHandler(new MonitoringHandler<>("refreshVariables", refreshVariablesHandler -> {
                            //logger.info("refreshVar : " + getTimeElapsed(refreshVarStart));
                            if(refreshVariablesHandler.succeeded()) {
                                Long removeStart = new Date().toInstant().toEpochMilli();
                                dagElementDao.removeDagElementAsyncIdMap(asyncId, dagElementId) //redundant in case of sql.
                                        .setHandler(new MonitoringHandler<>("removeDagElementIdFromAsyncTask", removeDagElementHandler -> {
                                    if (removeDagElementHandler.failed()) {
                                        future.fail(removeDagElementHandler.cause());
                                        return;
                                    }
                                    //logger.info("remove time: " + getTimeElapsed(removeStart));
                                    Long nextIdFetchStart = new Date().toInstant().toEpochMilli();
                                    dagElementDao.getNextId(dagElementId, false/*so that if nextElem was not able to get started, we dont stop here in the next run.*/).
                                            setHandler(new MonitoringHandler<>("getNextDagElementId", getNextDagElements -> {
                                        //logger.info("nextId fetch: " + getTimeElapsed(nextIdFetchStart));
                                        if(getNextDagElements.succeeded()) {
                                            if(getNextDagElements.result().size() == 0) {
                                                handleNoNextDagElement(asyncId).setHandler(noNextDagElementHandler -> {
                                                    if(noNextDagElementHandler.succeeded()) {
                                                        countConcurrentRequest.decrementAndGet();
                                                        orchestratorAsyncTaskDao.removeOrchestratorAsyncId(asyncId, dagElementId).setHandler(removeHandler -> {
                                                            if (removeHandler.succeeded()) {
                                                                future.complete();
                                                            } else {
                                                                future.fail(removeHandler.cause());
                                                            }
                                                        });
                                                    } else {
                                                        countConcurrentRequest.decrementAndGet();
                                                        future.fail("No next dag element found for asyncId: " + asyncId + ", dagElementId: " + dagElementId);
                                                    }

                                                });
                                            } else {
                                                callNextElements(getNextDagElements.result(), asyncId, dagElementId, vertx, toBeDebugged)
                                                        .setHandler(nextElementCallHandler -> {
                                                    countConcurrentRequest.decrementAndGet();
                                                    if(nextElementCallHandler.succeeded()) {
                                                        orchestratorAsyncTaskDao.removeOrchestratorAsyncId(asyncId, dagElementId).setHandler(removeHandler -> {
                                                            if (removeHandler.succeeded()) {
                                                                future.complete();
                                                            } else {
                                                                future.fail(removeHandler.cause());
                                                            }
                                                        });
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
                                logger.error("Failed to persist DAG vertex " + asyncId + "/" + dagElementId,
                                        refreshVariablesHandler.cause());
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
            if (isAsyncTaskDoneHandler.failed()) {
                future.fail(isAsyncTaskDoneHandler.cause());
                return;
            }
            if(isAsyncTaskDoneHandler.result() <= 1) {
                //terminate the asyncTask
                asyncTaskDao.update(asyncId, new HashMap<String, Object>() {
                    {
                        put("taskStatus", AsyncTask.TaskStatus.SUCCESS);
                        put("result", null);
                    }
                }).setHandler(updateHandler -> {
                    if(updateHandler.failed()) {
                        logger.error("Failed to update async task status to SUCCESS for asyncId: " + asyncId, updateHandler.cause());
                        future.fail(updateHandler.cause());
                        return;
                    }
                    storageDao.cleanupAsyncId(asyncId).setHandler(cleanupHandler -> {
                        if (cleanupHandler.failed()) {
                            logger.warn("Cleanup storage for asyncId " + asyncId + " warning: " + cleanupHandler.cause().getMessage());
                        }
                        future.complete();
                    });
                });
            } else {
                //dont' do anything
                future.complete();
            }
        });
        return future;
    }

    Future<Void> refreshVariables(String asyncId, String dagElementId, Map<String, Object> result, Vertx vertx) {
        Future<DagVertexOutput> prepared = Future.future();
        vertx.<DagVertexOutput>executeBlocking(blocking -> {
            try {
                blocking.complete(prepareVertexOutput(asyncId, dagElementId, result));
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, prepared);

        return prepared.compose(output ->
                persistArrays(asyncId, output.getArrayMap().entrySet().iterator()).compose(ignored -> {
                    List<in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable> variables = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : output.getVariableMap().entrySet()) {
                        in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable variable =
                                new in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable();
                        variable.setId(entry.getKey());
                        variable.setValue(entry.getValue());
                        variables.add(variable);
                    }
                    return variables.isEmpty() ? Future.<Void>succeededFuture()
                            : variableValueDao.updateVariablesBatch(asyncId, variables);
                }).compose(ignored -> {
                    Future<String> serialized = Future.future();
                    vertx.<String>executeBlocking(blocking -> {
                        try {
                            blocking.complete(objectMapper.writeValueAsString(output));
                        } catch (Exception e) {
                            blocking.fail(e);
                        }
                    }, false, serialized);
                    return serialized.compose(json -> storageDao.storeDagVertexOutput(asyncId, dagElementId, json));
                }));
    }

    private Future<Void> persistArrays(String asyncId, Iterator<Map.Entry<String, Map<String, Object>>> arrays) {
        if (!arrays.hasNext()) {
            return Future.succeededFuture();
        }
        Map.Entry<String, Map<String, Object>> array = arrays.next();
        return variableValueDao.storeArrayValueBatch(asyncId, array.getKey(), "", array.getValue())
                .compose(ignored -> persistArrays(asyncId, arrays));
    }

    private DagVertexOutput prepareVertexOutput(String asyncId, String dagElementId, Map<String, Object> result) {
        DagVertexOutput vertexOutput = new DagVertexOutput(asyncId, dagElementId);

        for(String key : result.keySet()) {
            if ("binaryArrayFiles".equals(key)) {
                if (!(result.get(key) instanceof Map) || !((Map<?, ?>) result.get(key)).isEmpty()) {
                    throw new IllegalArgumentException("Binary array results require an out-of-band upload");
                }
                continue;
            }
            if(Constants.arrayIndex.equalsIgnoreCase(key)) {
                if (!(result.get(key) instanceof Map)) {
                    throw new IllegalArgumentException("Array results must be a map");
                }
                Map<?, ?> arrayMap = (Map<?, ?>) result.get(key);
                for (Map.Entry<?, ?> array : arrayMap.entrySet()) {
                    if (!(array.getKey() instanceof String)) {
                        throw new IllegalArgumentException("Array IDs must be strings");
                    }
                    String arrayId = (String) array.getKey();
                    if(arrayId.contains("func")) {
                        continue;
                    }
                    if (!(array.getValue() instanceof Map)) {
                        throw new IllegalArgumentException("Array indices must be a map for " + arrayId);
                    }
                    Map<String, Object> arrayIndexMap = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> index : ((Map<?, ?>) array.getValue()).entrySet()) {
                        if (!(index.getKey() instanceof String)) {
                            throw new IllegalArgumentException("Array indices must be strings for " + arrayId);
                        }
                        arrayIndexMap.put((String) index.getKey(), index.getValue());
                    }
                    vertexOutput.getArrayMap().put(arrayId, arrayIndexMap);
                }
            } else {
                if(key.contains("func")) {
                    continue;
                }
                vertexOutput.getVariableMap().put(key, result.get(key));
            }
        }
        return vertexOutput;
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
                        if (removedHandler.failed()) {
                            logger.error("Failed to remove dependency for " + dagElementId + " -> " + nextDagElementId, removedHandler.cause());
                            future.fail(removedHandler.cause());
                            return;
                        }
                        logger.info("dependencyRemove: " + getTimeElapsed(dependencyRemoveStart));
                        callNextElementIfIsNotDependent(asyncId, dagElementId, vertx, nextDagElementId, future, toBeDebugged);
                    }));
        }
        CompositeFuture.join(futures).setHandler(handler -> {
            if(handler.failed()) {
                logger.error("Failed to call next elements", handler.cause());
                callNextElementFuture.fail(handler.cause());
                return;
            }
            callNextElementFuture.complete();
        });
        return callNextElementFuture;
    }

    private void callNextElementIfIsNotDependent(String asyncId, String dagElementId, Vertx vertx, String nextDagElementId,
                                                 Future<Void> future, Boolean toBeDebugged) {
        Long noDependencyCheckStart = new Date().toInstant().toEpochMilli();
        dagElementDao.isDagElementStillDependent(nextDagElementId)
                .setHandler(new MonitoringHandler<>("isDagElementStillDependent", dependenceHandler -> {
            //logger.info("noDependencyCheck: " + getTimeElapsed(noDependencyCheckStart));
            if(dependenceHandler.failed()) {
                logger.error("Failed to check dependency for " + nextDagElementId, dependenceHandler.cause());
                future.fail(dependenceHandler.cause());
                return;
            }
            if (!dependenceHandler.result()) {
                Long lockCheckStart = new Date().toInstant().toEpochMilli();
                attainLock(nextDagElementId).setHandler(new MonitoringHandler<>("attainLockOnDagElementId", attainer -> {
                    //logger.info("lockCheck: " + getTimeElapsed(lockCheckStart));
                    if(attainer.result()) {
                        logger.info(dagElementId + " calling for " + nextDagElementId);
                        Long orchestratorCallStart = new Date().toInstant().toEpochMilli();
                        runService.runDagElementId(asyncId, nextDagElementId, vertx, toBeDebugged)
                                .setHandler(new MonitoringHandler<>("runDagElementId", runHandler -> {
                            //logger.info("orchestratorAPI: " + getTimeElapsed(orchestratorCallStart));
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

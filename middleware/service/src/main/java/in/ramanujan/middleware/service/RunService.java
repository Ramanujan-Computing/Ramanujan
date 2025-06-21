package in.ramanujan.middleware.service;

import in.ramanujan.data.db.dao.*;
import in.ramanujan.middleware.service.future.CommonCodeStoreFutureCaller;
import in.ramanujan.middleware.service.future.FutureCaller;
import in.ramanujan.middleware.service.future.StoreDagElementCode;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.data.KafkaManagerApiCaller;
import in.ramanujan.data.OrchestrationApiCaller;
import in.ramanujan.developer.console.model.pojo.FirstDebugPointPayload;

import in.ramanujan.translation.codeConverter.BasicDagElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.utils.Constants;
import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RunService {

    @Autowired
    private OrchestrationApiCaller orchestrationApiCaller;

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private OrchestratorAsyncTaskDao orchestratorAsyncTaskDao;

    @Autowired
    private DagElementDao dagElementDao;

    @Autowired
    private VariableValueDao variableValueDao;

    @Autowired
    private DagElementVariableDao dagElementVariableDao;

    @Autowired
    private KafkaManagerApiCaller kafkaManagerApiCaller;

    @Autowired
    private StorageDao storageDao;

    private Set<String> dagElementIdRan = new HashSet<>();

    private Logger logger = LoggerFactory.getLogger(RunService.class);

    public Future<String> runCode(final TranslateResponse translateResponse, Vertx vertx, Boolean toBeDebugged) {
        Future<String> future = Future.future();
        addAsyncTask().setHandler(new MonitoringHandler<>("asyncTaskAdd", asyncTaskInsert -> {
            String asyncId = asyncTaskInsert.result();
            if(!toBeDebugged) {
                future.complete(asyncId);
            }
            initDbEntries(asyncId, translateResponse).setHandler(new MonitoringHandler<>("initDbEntries", dbOperationHandler -> {
                logger.info("InitDB done for " + asyncId);
                if (toBeDebugged) {
                    future.complete(asyncId);
                    return;
                }
                if(dbOperationHandler.succeeded()) {
                    runDagElementId(asyncId, translateResponse.getFirstDagElement().getId(), vertx, toBeDebugged)
                            .setHandler(new MonitoringHandler<>("runDagElementId", runDagHandler -> {
                    if (runDagHandler.succeeded()) {
                        kafkaManagerApiCaller.callEventApi(asyncId, translateResponse.getFirstDagElement().getId(), toBeDebugged);
                    } else {
                        logger.error("Failed to run dag element id: " + translateResponse.getFirstDagElement().getId(), runDagHandler.cause());
                        future.fail(runDagHandler.cause());
                    }
                    }));
                }
            }));
        }));
        return future;
    }

    private Future<Void> initDbEntries(String asyncId, TranslateResponse translateResponse) {
        List<DagElement> dagElementList = translateResponse.getDagElementList();
        Map<String, String> dagElementAndCodeMap = translateResponse.getCodeAndDagElementMap();
        Future<Void> future = Future.future();
        List<Future> dbOperations = new ArrayList<>();
        List<String> dagElementIds = new ArrayList<>();
        FutureCaller currFutureObj, prevFutureObj;
        /*
        * Storage push takes memory additionally. Making it sequential to stop possible OOM.
        */

        Set<String> variableAndArrayAlreadyPopulated = new HashSet<>();

        currFutureObj = new CommonCodeStoreFutureCaller(null, asyncId, translateResponse.getCommonFunctionCode(), storageDao);
//        dbOperations.add(storageDao.storeCommonCode(asyncId, translateResponse.getCommonFunctionCode()));
        for(DagElement dagElement : dagElementList) {
            dagElementIds.add(dagElement.getId());
            dbOperations.add(storageDao.storeDagElement(dagElement));
            prevFutureObj = currFutureObj;
            currFutureObj = new StoreDagElementCode(prevFutureObj, dagElement.getId(), dagElementAndCodeMap.get(dagElement.getId()), storageDao);
            // dbOperations.add(storageDao.storeDagElementCode(dagElement.getId(), dagElementAndCodeMap.get(dagElement.getId())));
            // Batch variable creation
            List<Variable> variablesToCreate = new ArrayList<>();
            for(Variable variable : dagElement.getVariableMap().values()) {
                if(variable.getId() != null && variable.getId().contains("func")) {
                    continue;
                }
                if(!variableAndArrayAlreadyPopulated.contains(variable.getId())) {
                    variablesToCreate.add(variable);
                    variableAndArrayAlreadyPopulated.add(variable.getId());
                }
            }
            if (!variablesToCreate.isEmpty()) {
                dbOperations.add(variableValueDao.createVariablesBatch(asyncId, variablesToCreate));
            }
            for(Array array : dagElement.getArrayMap().values()) {
                if(array.getId() != null && array.getId().contains("func")) {
                    continue;
                }
                if(!variableAndArrayAlreadyPopulated.contains(array.getId())) {
                    if(array.getName() == null) {
                        int a = 1;
                        a = a + 1;
                    }
                    dbOperations.add(variableValueDao.createVariableNameIdMap(asyncId, array.getId(), array.getName()));
                    for(String index : array.getValues().keySet()) {
                        dbOperations.add(variableValueDao.storeArrayValue(asyncId, array.getId(), array.getName(), index,
                                array.getValues().get(index)));
                    }
                    variableAndArrayAlreadyPopulated.add(array.getId());
                }

            }
            // Batch addDagElementDependency
            List<String> nextDagElementIds = new ArrayList<>();
            for(DagElement nextDagElement : dagElement.getNextElements()) {
                nextDagElementIds.add(nextDagElement.getId());
            }
            if (!nextDagElementIds.isEmpty()) {
                dbOperations.add(dagElementDao.addDagElementDependencies(dagElement.getId(), nextDagElementIds));
            }
        }
        dbOperations.add(dagElementDao.mapDagElementToAsyncId(asyncId, dagElementIds));

        final FutureCaller futureCaller = currFutureObj;
        CompositeFuture.all(dbOperations).setHandler(handler -> {
            if(handler.succeeded()) {
                futureCaller.call().setHandler(futureCallerHandler -> {
                    if(futureCallerHandler.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(futureCallerHandler.cause());
                    }
                });

            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }



    private void createUserUnderstandableResult(Map<String, Variable> variableMap, Map<String, Array> arrayMap, Map<String, Object> resultMao, Map<String, Object> result) {
        for(String resultId : resultMao.keySet()) {
            Object value = resultMao.get(resultId);
            if(Constants.arrayIndex.equals(resultId)) {
                //Map<String, Map<String, Object>> arrayMap == ;
                for(String arrayId : ((Map<String, Map<String, Object>>) value).keySet()) {
                    Map<String, Object> indexMap = ((Map<String, Map<String, Object>>) value).get(arrayId);
                    Array array = arrayMap.get(arrayId);
                    if(array != null) {
                        result.put(array.getName(), indexMap);
                    }
                }
            } else {
                Variable variable = variableMap.get(resultId);
                if(variable != null) {
                    result.put(variable.getName(), value);
                }
            }
        }
    }

    private Future<String> addAsyncTask() {
        Future<String> future = Future.future();
        String taskId = UUID.randomUUID().toString();
        AsyncTask asyncTask = new AsyncTask();
        asyncTask.setTaskId(taskId);
        asyncTask.setTaskStatus(AsyncTask.TaskStatus.PENDING);
        asyncTaskDao.insert(asyncTask).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete(taskId);
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }


    public Future<Void> runDagElementId(String asyncId, String dagElementId, Vertx vertx, Boolean toBeDebugged) {
        Future<Void> future = Future.future();
        storageDao.getDagElement(dagElementId).setHandler(handler -> {
            BasicDagElement basicDagElement = handler.result();
            //TODO: write the logic
            refreshVariablesAndProvideOrchestratorAsyncId(asyncId, basicDagElement).setHandler(refreshVariablesHandler -> {
                if (refreshVariablesHandler.succeeded()) {
                    final String orchestratorAsyncId = refreshVariablesHandler.result();
                    logger.info("Going to run for " + asyncId + "; dagElementId " + dagElementId);
                    forceRunOnOrchestrator(asyncId, dagElementId, vertx, toBeDebugged, basicDagElement, orchestratorAsyncId, future);
                } else {
                    future.fail(refreshVariablesHandler.cause());
                }
            });
        });
        return future;
    }

    private void forceRunOnOrchestrator(String asyncId, String dagElementId, Vertx vertx, Boolean toBeDebugged, BasicDagElement basicDagElement, String orchestratorAsyncId, Future<Void> future) {
        orchestrationApiCaller.runCode(asyncId,
                        basicDagElement.getFirstCommandId(), basicDagElement.getId(), vertx, orchestratorAsyncId, toBeDebugged, basicDagElement.getCommaSeparatedDebugPoints())
                .setHandler(orchestratorCallHandler -> {
                    if(orchestratorCallHandler.failed()) {
                        logger.error("Failed to run dag element id: " + dagElementId, orchestratorCallHandler.cause());
                        forceRunOnOrchestrator(asyncId, dagElementId, vertx, toBeDebugged, basicDagElement, orchestratorAsyncId, future);
                    }
                    if(dagElementIdRan.contains(dagElementId)) {
                        logger.info("ALREADY THERE: " + dagElementId);
                    }
                    dagElementIdRan.add(dagElementId);
                    future.complete();
                });
    }

    private Future<String> refreshVariablesAndProvideOrchestratorAsyncId(String asyncId, BasicDagElement basicDagElement) {
        Future<String> future = Future.future();
        RuleEngineInput ruleEngineInput = basicDagElement.getRuleEngineInput();
        String orchestratorAsyncId = UUID.randomUUID().toString();
        try {
            Long dbGetStart = new Date().toInstant().toEpochMilli();
            List<Future> futureList = new ArrayList<>();
            for(Variable variable : ruleEngineInput.getVariables()) {
                futureList.add(refreshVariableValue(asyncId, variable.getId(), variable));
            }
            for(Array array : ruleEngineInput.getArrays()) {
                futureList.add(refreshArrayIndexValue(asyncId, array, array.getId()));
            }
            futureList.add(dagElementDao.setDagElementAndOrchestratorAsyncIdMapping(basicDagElement.getId(), orchestratorAsyncId));
            CompositeFuture.all(futureList).setHandler(handler -> {
                logger.info("dbGet: " + (new Date().toInstant().toEpochMilli() - dbGetStart));
                if(handler.succeeded()) {
                    try {
                        Long storagePutStart = new Date().toInstant().toEpochMilli();
                        storageDao.storeDagElementInput(orchestratorAsyncId, basicDagElement.getRuleEngineInput()).setHandler(storageDaoHandler -> {
                            logger.info("storagePut: " + (new Date().toInstant().toEpochMilli() - storagePutStart));
                            if(storageDaoHandler.failed()) {
                                future.fail(storageDaoHandler.cause());
                                return;
                            }
                            future.complete(orchestratorAsyncId);
                        });
                    } catch (Exception e) {
                        future.fail(e);
                    }
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    private Future<Void> refreshArrayIndexValue(String asyncId, Array array, String arrayId) throws Exception {

        Future<Void> future = Future.future();
        variableValueDao.getArrayValues(asyncId, arrayId).setHandler(selectHandler -> {
           if(selectHandler.succeeded()) {
               Map<String, Object> indexMap = selectHandler.result();
               if(indexMap != null) {
                   array.getValues().putAll(indexMap);
               }
               future.complete();
           } else {
               future.fail(selectHandler.cause());
           }
        });


        //        Future<Void> future = Future.future();
//        variableValueDao.getVariableValue(asyncId, variableId).setHandler(getVariableHandler -> {
//            if(getVariableHandler.succeeded()) {
//                if(getVariableHandler.result() == null) {
//                    future.complete();
//                    return;
//                }
//                Object variableValue = getVariableHandler.result();
//                dagElementVariableDao.insertArrayValue(basicDagElement.getId(), arrayId, index, variableValue).setHandler(storeHandler -> {
//                    if(storeHandler.succeeded()) {
//                        future.complete();
//                    } else {
//                        future.fail(storeHandler.cause());
//                    }
//                });
//            } else {
//                future.fail(getVariableHandler.cause());
//            }
//        });
        return future;
    }

    private Future<Void> refreshVariableValue(String asyncId, String variableId, Variable variable) throws Exception {
        Future<Void> future = Future.future();
        variableValueDao.getVariableValue(asyncId, variableId).setHandler(getVariableHandler -> {
           if(getVariableHandler.succeeded()) {
               if(getVariableHandler.result() == null) {
                   future.complete();
                   return;
               }
               Object variableValue = getVariableHandler.result();
               variable.setValue(variableValue);
               future.complete();
           } else {
               future.fail(getVariableHandler.cause());
           }
        });
        return future;
    }

    public Future<Void> addDebugPoints(String asyncId, String firstDagElementId, FirstDebugPointPayload payload, Vertx vertx) {
        Future<Void> future = Future.future();
        List<Future> futureList = new ArrayList<>();
        for(FirstDebugPointPayload.DagElementIdDebugPointPair dagElementIdDebugPointPair : payload.getDagElementIdDebugPointPairs()) {
            futureList.add(dagElementDao.addDebugPointsToDagElement(dagElementIdDebugPointPair.getDagElementId(), dagElementIdDebugPointPair.getCommaSeparatedPoints()));
        }
        CompositeFuture.all(futureList).setHandler(handler -> {
            if(handler.succeeded()) {
                runDagElementId(asyncId, firstDagElementId, vertx, true).setHandler(runDagElementIdHandler -> {
                    if(runDagElementIdHandler.succeeded()) {
                        kafkaManagerApiCaller.callEventApi(asyncId, firstDagElementId, true).setHandler(kafkaCallHandler -> {
                            future.complete();

                        });
                    } else{
                        future.fail(runDagElementIdHandler.cause());
                    }
                });
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }


}

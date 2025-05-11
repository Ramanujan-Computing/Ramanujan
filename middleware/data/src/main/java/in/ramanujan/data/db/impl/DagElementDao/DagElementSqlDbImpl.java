package in.ramanujan.data.db.impl.DagElementDao;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.data.db.dao.DagElementDao;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.DagElementMetadata;
import in.ramanujan.db.layer.schema.DagElementOrchestratorAsyncIdMapping;
import in.ramanujan.db.layer.schema.DagElementRelationShip;
import in.ramanujan.db.layer.schema.OrchestratorMiddlewareMapping;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.middleware.base.utils.Splitter;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.BasicDagElement;
import in.ramanujan.translation.codeConverter.DagElement;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static in.ramanujan.db.layer.constants.Keys.DAG_ELEMENT_ID;

@Component
public class DagElementSqlDbImpl implements DagElementDao {

    @Autowired
    private QueryExecutor queryExecutor;

    @Override
    public Future<Void> setDagElementAndOrchestratorAsyncIdMapping(String dagElementId, String orchestratorAsyncId) {
        Future<Void> future = Future.future();
        final DagElementOrchestratorAsyncIdMapping dagElementOrchestratorAsyncIdMapping = new DagElementOrchestratorAsyncIdMapping();
        dagElementOrchestratorAsyncIdMapping.setDagElementId(dagElementId);
        dagElementOrchestratorAsyncIdMapping.setOrchestratorAsyncId(orchestratorAsyncId);
        try {
            queryExecutor.execute(dagElementOrchestratorAsyncIdMapping, DAG_ELEMENT_ID, QueryType.INSERT).setHandler(handler -> {
                if (handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<String> getDagElementAndOrchestratorAsyncIdMapping(String dagElementId) {
        Future<String> future = Future.future();
        DagElementOrchestratorAsyncIdMapping dagElementOrchestratorAsyncIdMapping = new DagElementOrchestratorAsyncIdMapping();
        dagElementOrchestratorAsyncIdMapping.setDagElementId(dagElementId);
        try {
            queryExecutor.execute(dagElementOrchestratorAsyncIdMapping, DAG_ELEMENT_ID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   if(handler.result().size() == 0) {
                       future.complete();
                       return;
                   }
                   future.complete((String) handler.result().get(0));
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> addDebugPointsToDagElement(String dagElementId, String commaSeparatedDebugLines) {
        Future<Void> future = Future.future();
        DagElementMetadata dagElementMetadata = new DagElementMetadata();
        dagElementMetadata.setDagElementId(dagElementId);
        dagElementMetadata.setDebugPoints(commaSeparatedDebugLines);
        try {
            queryExecutor.execute(dagElementMetadata, DAG_ELEMENT_ID, QueryType.UPDATE).setHandler(handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception ex) {
            future.fail(ex);
        }
        return future;
    }

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 1. Add the ruleEngine into parts as dagElement sql row
     * 2. Add entry in dagElementMetadata
     * */
    @Override
    public Future<Void> addElement(DagElement dagElement) {
        Future<Void> future = Future.future();
        try {
            List<Future> futureList = new ArrayList<>();
            RuleEngineInput ruleEngineInput = dagElement.getRuleEngineInput();
            String ruleEngineInputStr = objectMapper.writeValueAsString(ruleEngineInput);
            List<String> ruleEngineInputSubStrings = Splitter.splitString(ruleEngineInputStr, 65530);

            DagElementMetadata dagElementMetadata = new DagElementMetadata();
            dagElementMetadata.setDagElementId(dagElement.getId());
            dagElementMetadata.setFirstCommandId(dagElement.getFirstCommandId());
            dagElementMetadata.setMaxPart(ruleEngineInputSubStrings.size());

            futureList.add(queryExecutor.execute(dagElementMetadata, DAG_ELEMENT_ID, QueryType.INSERT));

            int part = 0;
            for(String ruleEngineInputSubString : ruleEngineInputSubStrings) {
                in.ramanujan.db.layer.schema.DagElement dagElementSchema = new in.ramanujan.db.layer.schema.DagElement();
                dagElementSchema.setDagElementId(dagElement.getId());
                dagElementSchema.setObject(ruleEngineInputSubString);
                dagElementSchema.setPart(part++);
                futureList.add(queryExecutor.execute(dagElementSchema, null, QueryType.INSERT));
            }

            CompositeFuture.all(futureList).setHandler(handler -> {
               if(handler.succeeded()) {
                   future.complete();
               } else {
                   future.fail(handler.cause());
               }
            });

        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<List<String>> getNextId(String dagElementId, Boolean onlyAliveRelationsToBeReturned) {
        Future<List<String>> future = Future.future();
        try {
            DagElementRelationShip dagElementRelationShip = new DagElementRelationShip();
            dagElementRelationShip.setDagElementId(dagElementId);

            queryExecutor.execute(dagElementRelationShip, DAG_ELEMENT_ID, QueryType.SELECT).setHandler(handler -> {
                if(handler.succeeded()) {
                    List<Object> list = handler.result();
                    List<String> nextIds = new ArrayList<>();
                    for(Object listObj : list) {
                        DagElementRelationShip dagElementRelationShipDbResult = (DagElementRelationShip) listObj;
                        if(!onlyAliveRelationsToBeReturned || "true".equalsIgnoreCase(dagElementRelationShipDbResult.getRelation())) {
                            nextIds.add(dagElementRelationShipDbResult.getNextDagElementId());
                        }
                    }
                    future.complete(nextIds);
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> updateVariableMap(String dagElementId, Map<String, Variable> variableMap) {
        return null;
    }

    @Override
    public Future<Void> updateArrayMap(String dagElementId, Map<String, Array> arrayMap) {
        return null;
    }

    @Override
    public Future<Void> addDagElementDependency(String dagElementId, String dependingDagElementId) {
        Future<Void> future = Future.future();
        try {
            DagElementRelationShip dagElementRelationShip = new DagElementRelationShip();
            dagElementRelationShip.setDagElementId(dagElementId);
            dagElementRelationShip.setNextDagElementId(dependingDagElementId);
            dagElementRelationShip.setRelation("true");
            queryExecutor.execute(dagElementRelationShip, null, QueryType.INSERT).setHandler(handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> removeDagElementDependency(String dagElementId, String dependingDagElementId) {
        Future<Void> future = Future.future();
        try {
            DagElementRelationShip dagElementRelationShip = new DagElementRelationShip();
            dagElementRelationShip.setDagElementId(dagElementId);
            dagElementRelationShip.setNextDagElementId(dependingDagElementId);
            dagElementRelationShip.setRelation("false");

            queryExecutor.execute(dagElementRelationShip, Keys.DE_ID_NEXT_DE_ID, QueryType.UPDATE).setHandler(handler -> {
               if(handler.succeeded()) {
                   future.complete();
               } else {
                   future.fail(handler.cause());
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Boolean> isDagElementStillDependent(String dagElementId) {
        Future<Boolean> future = Future.future();
        try {
            DagElementRelationShip dagElementRelationShip = new DagElementRelationShip();
            dagElementRelationShip.setNextDagElementId(dagElementId);
            queryExecutor.execute(dagElementRelationShip, Keys.NEXT_DAG_ELEMENT_ID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   List<Object> listObj = handler.result();
                   if(listObj.size() > 0) {
                       for(Object obj : listObj) {
                           if("true".equalsIgnoreCase(((DagElementRelationShip) obj).getRelation())) {
                               future.complete(true);
                               return;
                           }
                       }
                       future.complete(false);
                       return;
                   } else {
                       future.complete(false);
                       return;
                   }
//                   future.complete(listObj.size() > 0);
               } else {
                   future.fail(handler.cause());
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }

        return future;
    }

    @Override
    public Future<BasicDagElement> getDagElement(String dagElementId) {
        /**
         * It will return the DagElement object with id, ruleEngineInput, firstCommandId
         * */
        Future<BasicDagElement> future = Future.future();
        try {
            getDagElementMetaData(dagElementId).setHandler(metadataFetchHandler -> {
               if(metadataFetchHandler.succeeded()) {
                   final String firstCommandId = metadataFetchHandler.result().getFirstCommandId();
                   final int maxPart = metadataFetchHandler.result().getMaxPart();
                   BasicDagElement basicDagElement = new BasicDagElement();
                   basicDagElement.setCommaSeparatedDebugPoints(metadataFetchHandler.result().debugPoints);
                   basicDagElement.setId(dagElementId);
                   basicDagElement.setFirstCommandId(firstCommandId);
                   populateRuleEngineInDagElement(future, basicDagElement, maxPart);
               } else {
                   future.fail(metadataFetchHandler.cause());
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    private void populateRuleEngineInDagElement(Future<BasicDagElement> future, BasicDagElement basicDagElement, int maxPart) {
        try {
            String dagElementId = basicDagElement.getId();
            List<Future> futureList = new ArrayList<>();
            for (int i = 0; i < maxPart; i++) {
                in.ramanujan.db.layer.schema.DagElement dagElement = new in.ramanujan.db.layer.schema.DagElement();
                dagElement.setDagElementId(dagElementId);
                dagElement.setPart(i);
                futureList.add(queryExecutor.execute(dagElement, Keys.DAG_ELEMENT_ID_PART, QueryType.SELECT));
            }
            CompositeFuture.all(futureList).setHandler(handler -> {
                if(handler.succeeded()) {
                    try {
                        String ruleEngineInputStr = "";
                        for (Future queryFuture : futureList) {
                            in.ramanujan.db.layer.schema.DagElement dbDagElementInfo = (in.ramanujan.db.layer.schema.DagElement)
                                    ((Future<List<Object>>) queryFuture).result().get(0);
                            ruleEngineInputStr += dbDagElementInfo.getObject();
                        }
                        RuleEngineInput ruleEngineInput = objectMapper.readValue(ruleEngineInputStr, RuleEngineInput.class);
                        basicDagElement.setRuleEngineInput(ruleEngineInput);
                        future.complete(basicDagElement);
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
    }

    private Future<DagElementMetadata> getDagElementMetaData(String dagElementId) {
        Future<DagElementMetadata> future = Future.future();
        try {
            DagElementMetadata dagElementMetadata = new DagElementMetadata();
            dagElementMetadata.setDagElementId(dagElementId);

            queryExecutor.execute(dagElementMetadata, DAG_ELEMENT_ID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   List<Object> list = handler.result();
                   if(list.size() == 0) {
                       future.complete();
                   } else {
                       future.complete((DagElementMetadata) list.get(0));
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

    @Override
    public Future<Void> mapDagElementToAsyncId(String asyncId, List<String> dagElementIds) {
        // Redundant function in case of sql used
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> removeDagElementAsyncIdMap(String asyncId, String dagElementId) {
        // Redundant function in case of sql used
        return Future.succeededFuture();
    }

    @Override
    public Future<Boolean> isAsyncTaskDone(String asyncId) {
        Future<Boolean> future = Future.future();
        try {
            OrchestratorMiddlewareMapping orchestratorMiddlewareMapping = new OrchestratorMiddlewareMapping();
            orchestratorMiddlewareMapping.setMiddlewareAsyncId(asyncId);

            queryExecutor.execute(orchestratorMiddlewareMapping, Keys.MIDDLEWARE_ASYNC_ID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   future.complete(handler.result().size() == 0);
               } else {
                   future.fail(handler.cause());
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

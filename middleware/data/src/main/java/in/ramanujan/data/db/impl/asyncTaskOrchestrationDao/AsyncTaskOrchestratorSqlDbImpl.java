package in.ramanujan.data.db.impl.asyncTaskOrchestrationDao;

import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.data.db.dao.OrchestratorAsyncTaskDao;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.OrchestratorMiddlewareMapping;
import in.ramanujan.db.layer.utils.QueryExecutor;
import io.vertx.core.Future;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsyncTaskOrchestratorSqlDbImpl implements OrchestratorAsyncTaskDao {
    public QueryExecutor queryExecutor;

    @Override
    public Future<Void> addMapping(String asyncTaskId, String orchestrationId, String dagElementId) {
        Future<Void> future = Future.future();
        OrchestratorMiddlewareMapping orchestratorMiddlewareMapping = new OrchestratorMiddlewareMapping();
        orchestratorMiddlewareMapping.setOrchestratorAsyncId(orchestrationId);
        orchestratorMiddlewareMapping.setMiddlewareAsyncId(asyncTaskId);
        orchestratorMiddlewareMapping.setDagElementId(dagElementId);
        try {
            queryExecutor.execute(orchestratorMiddlewareMapping, null, QueryType.INSERT)
                    .setHandler(new MonitoringHandler<>("addOrchestratorMiddlewareMapping", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Map<String, String>> getMapping(String asyncTaskId) {
        Future<Map<String, String>> future = Future.future();
        Map<String, String> map = new HashMap<>();
        try {
            OrchestratorMiddlewareMapping orchestratorMiddlewareMapping = new OrchestratorMiddlewareMapping();
            orchestratorMiddlewareMapping.setMiddlewareAsyncId(asyncTaskId);
            queryExecutor.execute(orchestratorMiddlewareMapping, Keys.MIDDLEWARE_ASYNC_ID, QueryType.SELECT)
                    .setHandler(new MonitoringHandler<>("getOrchestratorMiddlewareMapping", handler -> {
                if(handler.succeeded()) {
                    List<Object> list = handler.result();
                    for(Object resultObj : list) {
                        OrchestratorMiddlewareMapping orchestratorMiddlewareMappingResultObj = (OrchestratorMiddlewareMapping) resultObj;
                        map.put(orchestratorMiddlewareMappingResultObj.getDagElementId(),
                                orchestratorMiddlewareMappingResultObj.getOrchestratorAsyncId());
                    }
                    future.complete(map);
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;

    }

    @Override
    public Future<Void> removeOrchestratorAsyncId(String asyncId, String dagElementId) {
        Future<Void> future = Future.future();
        OrchestratorMiddlewareMapping orchestratorMiddlewareMapping = new OrchestratorMiddlewareMapping();
        orchestratorMiddlewareMapping.setMiddlewareAsyncId(asyncId);
        orchestratorMiddlewareMapping.setDagElementId(dagElementId);
        try {
            queryExecutor.execute(orchestratorMiddlewareMapping, Keys.MIDDLEWARE_ASYNC_ID_DAG_ELEMENT_ID, QueryType.DELETE)
                    .setHandler(new MonitoringHandler<>("removeOrchestratorMiddlewareMapping", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> remove(String asyncId) {
        Future<Void> future = Future.future();
        OrchestratorMiddlewareMapping orchestratorMiddlewareMapping = new OrchestratorMiddlewareMapping();
        orchestratorMiddlewareMapping.setMiddlewareAsyncId(asyncId);
        try {
            queryExecutor.execute(orchestratorMiddlewareMapping, Keys.MIDDLEWARE_ASYNC_ID, QueryType.DELETE)
                    .setHandler(new MonitoringHandler<>("removeAllMappingsForAsyncId", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Boolean> isPresent(String asyncId, String dagElementId) {
        Future<Boolean> future = Future.future();
        OrchestratorMiddlewareMapping orchestratorMiddlewareMapping = new OrchestratorMiddlewareMapping();
        orchestratorMiddlewareMapping.setMiddlewareAsyncId(asyncId);
        orchestratorMiddlewareMapping.setDagElementId(dagElementId);
        try {
            queryExecutor.execute(orchestratorMiddlewareMapping, Keys.MIDDLEWARE_ASYNC_ID_DAG_ELEMENT_ID, QueryType.SELECT)
                    .setHandler(new MonitoringHandler<>("checkIfMappingIsPresent", handler -> {
                if(handler.succeeded()) {
                    List<Object> list = handler.result();
                    if(list == null || list.size() == 0) {
                        future.complete(false);
                    } else {
                        future.complete(true);
                    }
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

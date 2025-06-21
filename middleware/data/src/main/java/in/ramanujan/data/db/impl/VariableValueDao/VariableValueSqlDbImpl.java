package in.ramanujan.data.db.impl.VariableValueDao;

import in.ramanujan.data.db.dao.VariableValueDao;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.ArrayIdNameMap;
import in.ramanujan.db.layer.schema.VariableMapping;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.translation.codeConverter.pojo.ArrayMappingLite;
import in.ramanujan.translation.codeConverter.pojo.VariableMappingLite;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.db.layer.schema.ArrayMapping;
import in.ramanujan.translation.codeConverter.pojo.VariableAndArrayResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class VariableValueSqlDbImpl implements VariableValueDao {

    @Autowired
    private QueryExecutor queryExecutor;

    @Override
    public Future<Void> createVariable(String asyncId, String variableId, String variableName, Object value) {
        Future<Void> future = Future.future();
        VariableMapping variableMapping = new VariableMapping();
        variableMapping.setVariableId(variableId);
        variableMapping.setVariableName(variableName);
        variableMapping.setAsyncId(asyncId);
        if(value != null) {
            variableMapping.setObject(value.toString());
        }
        try {
            queryExecutor.execute(variableMapping, null, QueryType.INSERT).setHandler(
                    new MonitoringHandler<>("createVariable", handler -> {
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
    public Future<Void> createVariableNameIdMap(String asyncId, String arrayId, String arrayName) {
        Future<Void> future = Future.future();
        try {
            ArrayIdNameMap arrayIdNameMap = new ArrayIdNameMap();
            arrayIdNameMap.setAsyncId(asyncId);
            arrayIdNameMap.setArrayId(arrayId);
            arrayIdNameMap.setName(arrayName);
            queryExecutor.execute(arrayIdNameMap, null, QueryType.INSERT).setHandler(
                    new MonitoringHandler<>("createArrayNameId", handler -> {
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
    public Future<Void> storeVariableValue(String asyncId, String variableId, Object value) {
        Future<Void> future = Future.future();
        try {
            VariableMapping variableMapping = new VariableMapping();
            variableMapping.setAsyncId(asyncId);
            variableMapping.setVariableId(variableId);
            if(value != null) {
                variableMapping.setObject(value.toString());
            }
            queryExecutor.execute(variableMapping, Keys.ASYNC_ID_VARIABLE_ID, QueryType.UPDATE)
                    .setHandler(new MonitoringHandler<>("storeVariableValue", handler -> {
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
    public Future<Void> storeArrayValueBatch(String asyncId, String arrayId, String arrayName, Map<String, Object> indexValueMap) {
        Future<Void> future = Future.future();
        try {
            List<Object> list = new ArrayList<>();
            for(String index : indexValueMap.keySet()) {
                Object value = indexValueMap.get(index);
                ArrayMapping arrayMapping = new ArrayMapping();
                arrayMapping.setAsyncId(asyncId);
                arrayMapping.setArrayName("");
                arrayMapping.setArrayId(arrayId);
                arrayMapping.setIndexStr(index);
                if(value != null) {
                    arrayMapping.setObject(value.toString());
                }
                list.add(arrayMapping);
            }
            queryExecutor.execute(list.get(0), Keys.ASYNC_ID_ARRAY_ID_INDEX, QueryType.UPSERT, list)
                    .setHandler(new MonitoringHandler<>("storeArrayValue", handler -> {
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
    public Future<Void> storeArrayValue(String asyncId, String arrayId, String arrayName, String index, Object value) {
        Future<Void> future = Future.future();
        try {
            ArrayMapping arrayMapping = new ArrayMapping();
            arrayMapping.setAsyncId(asyncId);
            arrayMapping.setArrayName("");
            arrayMapping.setArrayId(arrayId);
            arrayMapping.setIndexStr(index);
            if(value != null) {
                arrayMapping.setObject(value.toString());
            }

            queryExecutor.execute(arrayMapping, Keys.ASYNC_ID_ARRAY_ID_INDEX, QueryType.UPSERT)
                    .setHandler(new MonitoringHandler<>("storeArrayValue", handler -> {
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
    public Future<Object> getVariableValue(String asyncId, String variableId) {
        Future<Object> future = Future.future();
        if(variableId.contains(":")) {
            try {
                ArrayMapping arrayMapping = new ArrayMapping();
                int indexOfFirstOpeningBracket = variableId.indexOf(":");
                String arrayId = variableId.substring(0, indexOfFirstOpeningBracket);
                String index = variableId.substring(indexOfFirstOpeningBracket);
                arrayMapping.setAsyncId(asyncId);
                arrayMapping.setArrayId(arrayId);
                arrayMapping.setIndexStr(index);
                queryExecutor.execute(arrayMapping, Keys.ASYNC_ID_ARRAY_ID_INDEX, QueryType.SELECT)
                        .setHandler(new MonitoringHandler<>("arrayValueGet", handler -> {
                    if(handler.succeeded()) {
                        List<Object> results = handler.result();
                        for(Object obj : results) {
                            ArrayMapping variableMappingObj = (ArrayMapping) obj;
                            future.complete(getObject(variableMappingObj.getObject()));
                            return;
                        }
                        future.complete();
                    } else {
                        future.fail(handler.cause());
                    }
                }));
            } catch (Exception e) {
                future.fail(e);
            }
        } else {
            try {
                VariableMapping variableMapping = new VariableMapping();
                variableMapping.setAsyncId(asyncId);
                variableMapping.setVariableId(variableId);

                queryExecutor.execute(variableMapping, Keys.ASYNC_ID_VARIABLE_ID, QueryType.SELECT)
                        .setHandler(new MonitoringHandler<>("variableValueGet", handler -> {
                    if(handler.succeeded()) {
                        List<Object> results = handler.result();
                        for(Object obj : results) {
                            VariableMapping variableMappingObj = (VariableMapping) obj;
                            future.complete(getObject(variableMappingObj.getObject()));
                            return;
                        }
                        future.complete();
                    } else {
                        future.fail(handler.cause());
                    }
                }));
            } catch (Exception e) {
                future.fail(e);
            }
        }
        return future;
    }

    @Override
    public Future<Map<String, Object>> getArrayValues(String asyncId, String arrayId) {
        Future<Map<String, Object>> future = Future.future();
        ArrayMapping arrayMapping = new ArrayMapping();
        arrayMapping.setAsyncId(asyncId);
        arrayMapping.setArrayId(arrayId);

        try {
            queryExecutor.execute(arrayMapping, Keys.ASYNC_ID_ARRAY_ID, QueryType.SELECT)
                    .setHandler(new MonitoringHandler<>("arrayValueGet", handler -> {
                if (handler.succeeded()) {
                    List<Object> objects = handler.result();
                    Map<String, Object> map = new HashMap<>();
                    for(Object object : objects) {
                        ArrayMapping returnedObj = (ArrayMapping) object;
                        map.put(returnedObj.getIndexStr(), getObject(returnedObj.getObject()));
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

    private Object getObject(String object) {
        try {
            return Integer.parseInt(object);
        } catch (Exception e) {

        }
        try {
            return Double.parseDouble(object);
        } catch (Exception e) {

        }
        return object;
    }

    @Override
    public Future<VariableAndArrayResult> getAllValuesForAsyncId(String asyncId) {
        Future<VariableAndArrayResult> future = Future.future();
        try {
            Future<Object> variableFetchFuture = Future.future();
            Future<Object> arrayFetchFuture = Future.future();
            VariableMapping variableMapping = new VariableMapping();
            variableMapping.setAsyncId(asyncId);
            queryExecutor.execute(variableMapping, Keys.ASYNC_ID, QueryType.SELECT)
                    .setHandler(new MonitoringHandler<>("getAllVariableForAsyncId", handler -> {
                if(handler.succeeded()) {
                    variableFetchFuture.complete(handler.result());
                } else {
                    variableFetchFuture.fail(handler.cause());
                }
            }));

            ArrayMapping arrayMapping = new ArrayMapping();
            arrayMapping.setAsyncId(asyncId);
            queryExecutor.execute(arrayMapping, Keys.ASYNC_ID, QueryType.SELECT)
                    .setHandler(new MonitoringHandler<>("getAllArrayForAsyncId", handler -> {
                if(handler.succeeded()) {
                    arrayFetchFuture.complete(handler.result());
                } else {
                    arrayFetchFuture.fail(handler.cause());
                }
            }));

            CompositeFuture.all(variableFetchFuture, arrayFetchFuture).setHandler(handler -> {
                if(handler.succeeded()) {
                    List<VariableMappingLite> variables = new ArrayList<>();
                    for (Object obj : (List<?>) variableFetchFuture.result()) {
                        if (obj instanceof VariableMapping) {
                            VariableMapping v = (VariableMapping) obj;
                            variables.add(new VariableMappingLite(
                                v.getVariableId(),
                                v.getVariableName(),
                                v.getAsyncId(),
                                getObject(v.getObject())
                            ));
                        }
                    }
                    List<ArrayMappingLite> arrays = new ArrayList<>();
                    for (Object obj : (List<?>) arrayFetchFuture.result()) {
                        if (obj instanceof ArrayMapping) {
                            ArrayMapping a = (ArrayMapping) obj;
                            arrays.add(new ArrayMappingLite(
                                a.getArrayId(),
                                a.getArrayName(),
                                a.getAsyncId(),
                                a.getIndexStr(),
                                getObject(a.getObject())
                            ));
                        }
                    }
                    VariableAndArrayResult result = new VariableAndArrayResult(variables, arrays);
                    future.complete(result);
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
    public Future<Void> deletedAllVariablesForAsyncId(String asyncId) {
        Future<Void> future = Future.future();
        try {
            Future<Void> variableFetchFuture = Future.future();
            Future<Void> arrayFetchFuture = Future.future();
            Future<Void> arrayNameIdMapDeleteFuture = Future.future();
            VariableMapping variableMapping = new VariableMapping();
            variableMapping.setAsyncId(asyncId);
            queryExecutor.execute(variableMapping, Keys.ASYNC_ID, QueryType.DELETE)
                    .setHandler(new MonitoringHandler<>("deleteAllVariableForAsyncId", handler -> {
                if(handler.succeeded()) {
                    variableFetchFuture.complete();
                } else {
                    variableFetchFuture.fail(handler.cause());
                }
            }));

            ArrayMapping arrayMapping = new ArrayMapping();
            arrayMapping.setAsyncId(asyncId);
            queryExecutor.execute(arrayMapping, Keys.ASYNC_ID, QueryType.DELETE)
                    .setHandler(new MonitoringHandler<>("deleteAllArrayForAsyncId", handler -> {
                if(handler.succeeded()) {
                    arrayFetchFuture.complete();
                } else {
                    arrayFetchFuture.fail(handler.cause());
                }
            }));
            ArrayIdNameMap arrayIdNameMap = new ArrayIdNameMap();
            arrayIdNameMap.setAsyncId(asyncId);
            queryExecutor.execute(arrayIdNameMap, Keys.ASYNC_ID, QueryType.DELETE)
                    .setHandler(new MonitoringHandler<>("deleteAllArrayMapForAsyncId", handler -> {
                if(handler.succeeded()) {
                    arrayNameIdMapDeleteFuture.complete();
                } else {
                    arrayNameIdMapDeleteFuture.fail(handler.cause());
                }
            }));
            CompositeFuture.all(variableFetchFuture, arrayFetchFuture).setHandler(handler -> {
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
    public Future<Void> createVariablesBatch(String asyncId, java.util.List<in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable> variables) {
        Future<Void> future = Future.future();
        try {
            List<Object> variableMappings = new ArrayList<>();
            for (in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable variable : variables) {
                in.ramanujan.db.layer.schema.VariableMapping variableMapping = new in.ramanujan.db.layer.schema.VariableMapping();
                variableMapping.setAsyncId(asyncId);
                variableMapping.setVariableId(variable.getId());
                variableMapping.setVariableName(variable.getName());
                if (variable.getValue() != null) {
                    variableMapping.setObject(variable.getValue().toString());
                }
                variableMappings.add(variableMapping);
            }
            if (!variableMappings.isEmpty()) {
                queryExecutor.execute(variableMappings.get(0), Keys.ASYNC_ID_VARIABLE_ID, QueryType.INSERT, variableMappings)
                        .setHandler(new MonitoringHandler<>("createVariablesBatch", handler -> {
                            if (handler.succeeded()) {
                                future.complete();
                            } else {
                                future.fail(handler.cause());
                            }
                        }));
            } else {
                future.complete();
            }
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

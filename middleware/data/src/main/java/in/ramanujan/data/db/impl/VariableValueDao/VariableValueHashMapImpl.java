package in.ramanujan.data.db.impl.VariableValueDao;

import in.ramanujan.data.db.dao.VariableValueDao;
import in.ramanujan.translation.codeConverter.pojo.VariableAndArrayResult;
import io.vertx.core.Future;

import java.util.HashMap;
import java.util.Map;


public class VariableValueHashMapImpl implements VariableValueDao {
    private Map<String, Map<String, Object>> variableValueMap;
    private Map<String, Map<String, Map<String, Object>>> arrayValueMap;
    private Map<String, String> variableIdAndNameMap;


    public void init() {
        variableValueMap = new HashMap<>();
        arrayValueMap = new HashMap<>();
        variableIdAndNameMap = new HashMap<>();
    }

    @Override
    public Future<Void> createVariable(String asyncId, String variableId, String variableName, Object value) {
        return null;
    }

    @Override
    public Future<Void> createVariableNameIdMap(String asyncId, String variableId, String variableName) {
        variableIdAndNameMap.put(variableId, variableName);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> storeVariableValue(String asyncId, String variableId, Object value) {
        Map<String, Object> map = variableValueMap.get(asyncId);
        if(map == null) {
            map = new HashMap<>();
            variableValueMap.put(asyncId, map);
        }
        map.put(variableId, value);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> storeArrayValueBatch(String asyncId, String arrayId, String arrayName, Map<String, Object> indexValueMap) {
        // Store all index-value pairs for the array in memory
        Map<String, Map<String, Object>> map = arrayValueMap.get(asyncId);
        if(map == null) {
            map = new HashMap<>();
            arrayValueMap.put(asyncId, map);
        }
        Map<String, Object> currentArrayIdMap = map.get(arrayId);
        if(currentArrayIdMap == null) {
            currentArrayIdMap = new HashMap<>();
            map.put(arrayId, currentArrayIdMap);
        }
        currentArrayIdMap.putAll(indexValueMap);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> storeArrayValue(String asyncId, String arrayId, String arrayName, String index, Object value) {
        Map<String, Map<String, Object>> map = arrayValueMap.get(asyncId);
        if(map == null) {
            map = new HashMap<>();
            arrayValueMap.put(asyncId, map);
        }
        Map<String, Object> currentArrayIdMap = map.get(arrayId);
        if(currentArrayIdMap == null) {
            currentArrayIdMap = new HashMap<>();
            map.put(arrayId, currentArrayIdMap);
        }
        currentArrayIdMap.put(index, value);
        return Future.succeededFuture();
    }

    @Override
    public Future<Object> getVariableValue(String asyncId, String variableId) {
        Map<String, Object> map = variableValueMap.get(asyncId);
        if(map == null) {
            return Future.succeededFuture();
        }
        if(map.containsKey(variableId)) {
            return Future.succeededFuture(map.get(variableId));
        }
        map = (Map) arrayValueMap.get(asyncId);
        if(map == null) {
            return Future.succeededFuture();
        }
        if(map.containsKey(variableId)) {
            return Future.succeededFuture(map.get(variableId));
        }

        return Future.succeededFuture(map.get(variableId));
    }

    @Override
    public Future<Map<String, Object>> getArrayValues(String asyncId, String arrayId) {
        return Future.succeededFuture();
    }

    @Override
    public Future<VariableAndArrayResult> getAllValuesForAsyncId(String asyncId) {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> variableMap = variableValueMap.get(asyncId);
        if(variableMap != null) {
            for(String variableId : variableMap.keySet()) {
                String name = variableIdAndNameMap.get(variableId);
                if(name != null) {
                    map.put(name, variableMap.get(variableId));
                }
            }
        }

        Map<String, Map<String, Object>> arrayMap = arrayValueMap.get(asyncId);
        if(arrayMap != null) {
            for(String arrayId : arrayMap.keySet()) {
                String name = variableIdAndNameMap.get(arrayId);
                if(name != null) {
                    map.put(name, arrayMap.get(arrayId));
                }
            }
        }

        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> deletedAllVariablesForAsyncId(String asyncId) {
        variableValueMap.remove(asyncId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> createVariablesBatch(String asyncId, java.util.List<in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable> variables) {
        Map<String, Object> map = variableValueMap.get(asyncId);
        if (map == null) {
            map = new HashMap<>();
            variableValueMap.put(asyncId, map);
        }
        for (in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable variable : variables) {
            map.put(variable.getId(), variable.getValue());
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> updateVariablesBatch(String asyncId, java.util.List<in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable> variables) {
        Map<String, Object> map = variableValueMap.get(asyncId);
        if (map == null) {
            map = new HashMap<>();
            variableValueMap.put(asyncId, map);
        }
        for (in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable variable : variables) {
            map.put(variable.getId(), variable.getValue());
        }
        return Future.succeededFuture();
    }
}

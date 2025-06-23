package in.ramanujan.data.db.dao;

import io.vertx.core.Future;
import org.springframework.stereotype.Component;
import in.ramanujan.translation.codeConverter.pojo.VariableAndArrayResult;

import java.util.Map;


/*
* store all the variableIds
* store all the index of array. For ex: there will be entry like: arrayId:index1_index2_..._indexN
* */

@Component
public interface VariableValueDao {
    /**
     * Batch create variables for a given asyncId
     */
    io.vertx.core.Future<Void> createVariablesBatch(final String asyncId, final java.util.List<in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable> variables);
    Future<Void> updateVariablesBatch(final String asyncId, final java.util.List<in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable> variables);
    public Future<Void> createVariableNameIdMap(final String asyncId, final String arrayId, final String arrayName);
    public Future<Void> storeVariableValue(final String asyncId, final String variableId, final Object value);
    public Future<Void> storeArrayValueBatch(final String asyncId, final String arrayId, final String arrayName, Map<String, Object> indexValueMap);
    public Future<Void> storeArrayValue(final String asyncId, final String arrayId, final String arrayName, final String index, final Object value);
    public Future<Void> createVariable(final String asyncId, final String variableId, String variableName, final Object value);
    /**
     * The caller of this method has to be refactored.
     * */
    public Future<Object> getVariableValue(final String asyncId, final String variableId);
    public Future<Map<String, Object>> getArrayValues(final String asyncId, final String arrayId);
    public Future<VariableAndArrayResult> getAllValuesForAsyncId(final String asyncId);
    public Future<Void> deletedAllVariablesForAsyncId(final String asyncId);
}

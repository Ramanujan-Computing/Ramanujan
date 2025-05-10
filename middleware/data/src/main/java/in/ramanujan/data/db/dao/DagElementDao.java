package in.ramanujan.data.db.dao;

import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.BasicDagElement;
import in.ramanujan.translation.codeConverter.DagElement;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public interface DagElementDao {
    public Future<Void> addElement(DagElement dagElement);
    public Future<List<String>> getNextId(String dagElementId, Boolean onlyAliveRelationsToBeReturned);
    public Future<Void> updateVariableMap(String dagElementId, Map<String, Variable> variableMap);
    public Future<Void> updateArrayMap(String dagElementId, Map<String, Array> arrayMap);
    public Future<Void> addDagElementDependency(String dagElementId, String dependingDagElementId);
    public Future<Void> removeDagElementDependency(String dagElementId, String dependingDagElementId);
    public Future<Boolean> isDagElementStillDependent(String dagElementId);
    public Future<BasicDagElement> getDagElement(String dagElementId);
    public Future<Void> mapDagElementToAsyncId(String asyncId, List<String> dagElementIds);
    public Future<Void> removeDagElementAsyncIdMap(String asyncId, String dagElementId);
    public Future<Boolean> isAsyncTaskDone(String asyncId);
    public Future<Void> setDagElementAndOrchestratorAsyncIdMapping(String dagElementId, String orchestratorAsyncId);
    public Future<String> getDagElementAndOrchestratorAsyncIdMapping(String dagElementId);
    public Future<Void> addDebugPointsToDagElement(String dagElementId, String commaSeparatedDebugLines);
}

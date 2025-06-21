package in.ramanujan.data.db.impl.DagElementDao;

import in.ramanujan.data.db.dao.DagElementDao;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.BasicDagElement;
import in.ramanujan.translation.codeConverter.DagElement;
import io.vertx.core.Future;

import java.util.*;


public class DagElementHashMapImpl implements DagElementDao {

    private Map<String, DagElement> dagElementMap;
    private Map<String, Set<String>> dagElementIdDependency;
    private Map<String, Set<String>> asyncTaskDagElementMap;


    public void init() {
        dagElementMap = new HashMap<>();
        dagElementIdDependency = new HashMap<>();
        asyncTaskDagElementMap = new HashMap<>();
    }

    @Override
    public Future<Void> addElement(DagElement dagElement) {
        dagElementMap.put(dagElement.getId(), dagElement);
        return Future.succeededFuture();
    }

    @Override
    public Future<List<String>> getNextId(String dagElementId, Boolean onlyAliveRelationsToBeReturned) {
        DagElement dagElement = dagElementMap.get(dagElementId);
        if(dagElement == null) {
            return Future.succeededFuture(new ArrayList<>());
        }
        List<String> nextIds = new ArrayList<>();
        for(DagElement nextElement : dagElement.getNextElements()) {
            nextIds.add(nextElement.getId());
        }
        return Future.succeededFuture(nextIds);
    }

    @Override
    public Future<Void> updateVariableMap(String dagElementId, Map<String, Variable> variableMap) {
        final DagElement dagElement = dagElementMap.get(dagElementId);
        if(dagElement == null) {
            return Future.succeededFuture();
        }
        dagElement.setVariableMap(variableMap);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> updateArrayMap(String dagElementId, Map<String, Array> arrayMap) {
        final DagElement dagElement = dagElementMap.get(dagElementId);
        if(dagElement == null) {
            return Future.succeededFuture();
        }
        dagElement.setArrayMap(arrayMap);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> addDagElementDependency(String dagElementId, String dependingDagElementId) {
        Set<String> dagElementIdsRequired = dagElementIdDependency.get(dependingDagElementId);
        if(dagElementIdsRequired == null) {
            dagElementIdsRequired = new HashSet<>();
            dagElementIdDependency.put(dependingDagElementId, dagElementIdsRequired);
        }
        dagElementIdsRequired.add(dagElementId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> removeDagElementDependency(String dagElementId, String dependingDagElementId) {
        dagElementIdDependency.get(dependingDagElementId).remove(dagElementId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Boolean> isDagElementStillDependent(String dagElementId) {
        return Future.succeededFuture(dagElementIdDependency.get(dagElementId).size() > 0);
    }

    @Override
    public Future<Void> mapDagElementToAsyncId(String asyncId, List<String> dagElementIds) {
        Set<String> set = asyncTaskDagElementMap.get(asyncId);
        if(!asyncTaskDagElementMap.containsKey(asyncId)) {
            set = new HashSet<>();
            asyncTaskDagElementMap.put(asyncId, set);
        }
        set.addAll(dagElementIds);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> removeDagElementAsyncIdMap(String asyncId, String dagElementId) {
        asyncTaskDagElementMap.get(asyncId).remove(dagElementId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Boolean> isAsyncTaskDone(String asyncId) {
        return Future.succeededFuture(asyncTaskDagElementMap.get(asyncId).size() == 0);
    }

    @Override
    public Future<Void> setDagElementAndOrchestratorAsyncIdMapping(String dagElementId, String orchestratorAsyncId) {
        return null;
    }

    @Override
    public Future<String> getDagElementAndOrchestratorAsyncIdMapping(String dagElementId) {
        return null;
    }

    @Override
    public Future<Void> addDebugPointsToDagElement(String dagElementId, String commaSeparatedDebugLines) {
        return null;
    }

    @Override
    public Future<BasicDagElement> getDagElement(String dagElementId) {
        return Future.succeededFuture(dagElementMap.get(dagElementId));
    }

    @Override
    public Future<Void> addDagElementDependencies(String dagElementId, java.util.List<String> nextDagElementIds) {
        for (String nextDagElementId : nextDagElementIds) {
            addDagElementDependency(dagElementId, nextDagElementId);
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> removeDagElementDependencies(String dagElementId, java.util.List<String> nextDagElementIds) {
        for (String nextDagElementId : nextDagElementIds) {
            removeDagElementDependency(dagElementId, nextDagElementId);
        }
        return Future.succeededFuture();
    }
}

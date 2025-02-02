package in.robinhood.ramanujan.data.db.impl.asyncTaskOrchestrationDao;

import in.robinhood.ramanujan.data.db.dao.OrchestratorAsyncTaskDao;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class AsyncTaskOrchestrationHashMapImpl implements OrchestratorAsyncTaskDao {

    private Map<String, Map<String, String>> map;


    public void init() {
        map = new HashMap<>();
    }

    @Override
    public Future<Void> addMapping(String asyncTaskId, final String orchestratorAsyncId, final String dagElementId) {
        Map<String, String> dagElementOrchestartorTaskIdMap = map.get(asyncTaskId);
        if(dagElementOrchestartorTaskIdMap == null) {
            dagElementOrchestartorTaskIdMap = new HashMap<>();
            map.put(asyncTaskId, dagElementOrchestartorTaskIdMap);
        }
        dagElementOrchestartorTaskIdMap.put(dagElementId, orchestratorAsyncId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Map<String, String>> getMapping(String asyncTaskId) {
        return Future.succeededFuture(this.map.get(asyncTaskId));
    }

    @Override
    public Future<Void> remove(String asyncId) {
        map.remove(asyncId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> removeOrchestratorAsyncId(String asyncId, final String dagElementId) {
        Map<String, String> orchestrationAsyncIds = map.get(asyncId);
        if(orchestrationAsyncIds == null) {
            return Future.failedFuture("asyncId not in dataStore");
        }
        orchestrationAsyncIds.remove(dagElementId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Boolean> isPresent(String asyncId, final String dagElementId) {
        Map<String, String> dagElementMap = map.get(asyncId);
        if(dagElementMap == null) {
            return Future.succeededFuture(false);
        }
        if(dagElementMap.containsKey(dagElementId)) {
            return Future.succeededFuture(true);
        } else {
            return Future.succeededFuture(false);
        }
    }
}

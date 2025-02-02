package in.robinhood.ramanujan.orchestrator.data.impl.asyncTaskHostMappingDaoImpl;

import in.robinhood.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.robinhood.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import io.vertx.core.Future;

import java.util.HashMap;
import java.util.Map;

public class AsyncTaskHostMappingDaoHashMapImpl implements AsyncTaskHostMappingDao {

    Map<String, AsyncTask> mapping;

    public void init() {
        mapping = new HashMap<>();
    }

    @Override
    public Future<Void> createMapping(AsyncTask asyncTask, String hostMachineId, Boolean resumeComputation) {
        /*
        * has to be upserted
        * */
        mapping.put(hostMachineId, asyncTask);
        return Future.succeededFuture();
    }

    @Override
    public Future<AsyncTask> getMapping(String hostMachineId) {
        return  Future.succeededFuture(mapping.get(hostMachineId));
    }

    @Override
    public Future<Void> deleteTask(String asyncTaskId) {
        return null;
    }

    @Override
    public Future<String> getHostForTask(String asyncTaskId) {
        return null;
    }

    @Override
    public Future<Void> update(String hostMachineId, AsyncTask asyncTask) {
        mapping.put(hostMachineId, asyncTask);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> removeMapping(String hostMachineId, String asyncTaskId) {
        mapping.remove(hostMachineId);
        return Future.succeededFuture();
    }
}

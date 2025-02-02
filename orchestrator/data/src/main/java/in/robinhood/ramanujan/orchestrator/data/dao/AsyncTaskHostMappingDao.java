package in.robinhood.ramanujan.orchestrator.data.dao;

import in.robinhood.ramanujan.orchestrator.base.pojo.AsyncTask;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

@Component
public interface AsyncTaskHostMappingDao {
    public Future<Void> createMapping(AsyncTask asyncTask, String hostMachineId, Boolean resumeComputation);
    public Future<AsyncTask> getMapping(String hostMachineId);
    public Future<String> getHostForTask(String asyncTaskId);
    public Future<Void> deleteTask(String asyncTaskId);
    public Future<Void> update(String hostMachineId, AsyncTask asyncTask);
    public Future<Void> removeMapping(String hostMachineId, String asyncTaskId);
}

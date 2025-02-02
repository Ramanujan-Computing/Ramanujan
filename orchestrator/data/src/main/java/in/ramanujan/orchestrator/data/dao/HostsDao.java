package in.ramanujan.orchestrator.data.dao;

import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

@Component
public interface HostsDao {
    public Future<String> getMachine(AsyncTask asyncTask, Boolean resumeComputation);
    public Future<AsyncTask> putMachineForComputation(String hostId);
}

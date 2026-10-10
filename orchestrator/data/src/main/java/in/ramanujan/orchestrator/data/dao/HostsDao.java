package in.ramanujan.orchestrator.data.dao;

import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

/**
 * Data Access Object interface for managing host machine assignment and queueing tasks for execution.
 */
@Component
public interface HostsDao {

    /**
     * Obtains an available machine for executing the specified asynchronous task.
     *
     * @param asyncTask         the task to be scheduled
     * @param resumeComputation whether the computation is being resumed from a previous state
     * @return a future containing the assigned host ID, or null if no host is currently available
     */
    public Future<String> getMachine(AsyncTask asyncTask, Boolean resumeComputation);

    /**
     * Registers an idle host machine as available for computation, returning a queued task if one exists.
     *
     * @param hostId the unique identifier of the host machine
     * @return a future containing an assigned task, or null if no tasks are waiting
     */
    public Future<AsyncTask> putMachineForComputation(String hostId);

    /**
     * Registers an idle host machine with real-time capability and resource telemetry,
     * matching it against waiting tasks using capability- and resource-aware scheduling.
     *
     * @param hostId    the unique identifier of the host machine
     * @param telemetry the real-time telemetry reported by the worker (CPU threads, RAM, rank, GPU)
     * @return a future containing the best matched task, or null if no tasks are waiting
     */
    public Future<AsyncTask> putMachineForComputation(String hostId, WorkerTelemetry telemetry);
}


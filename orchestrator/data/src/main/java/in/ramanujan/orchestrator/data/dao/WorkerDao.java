package in.ramanujan.orchestrator.data.dao;

import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import io.vertx.core.Future;

import java.util.List;

/**
 * Data Access Object interface for managing worker node registration, real-time telemetry,
 * heartbeat tracking, and eligible worker discovery for load-balanced task assignment.
 */
public interface WorkerDao {

    /**
     * Inserts or updates the telemetry and status metrics for a worker node upon receiving a ping.
     *
     * @param telemetry the dynamic telemetry metrics reported by the worker
     * @return a future completing when the telemetry is persisted
     */
    Future<Void> upsertWorkerTelemetry(WorkerTelemetry telemetry);

    /**
     * Updates the operational status of a worker node (e.g., OPEN, ENGAGED, OFFLINE).
     *
     * @param hostId the unique identifier of the worker host
     * @param status the new operational status string
     * @return a future completing when the status update is persisted
     */
    Future<Void> updateWorkerStatus(String hostId, String status);

    /**
     * Retrieves all available (OPEN) worker nodes whose last ping is at or after {@code minPingEpoch},
     * optionally filtered or matched against the requirements of the task's complexity profile.
     *
     * @param complexity   the complexity profile of the task needing assignment
     * @param minPingEpoch minimum timestamp (epoch ms) for a worker to be considered alive
     * @return a future containing the list of eligible worker telemetries
     */
    Future<List<WorkerTelemetry>> getEligibleWorkers(TaskComplexityProfile complexity, long minPingEpoch);

    /**
     * Retrieves the current telemetry and configuration of a worker node by its host ID.
     *
     * @param hostId the unique identifier of the worker host
     * @return a future containing the worker's telemetry, or null if not found
     */
    Future<WorkerTelemetry> getWorker(String hostId);

    /**
     * Prunes or marks workers as OFFLINE if their last ping timestamp is older than {@code staleThresholdEpoch}.
     *
     * @param staleThresholdEpoch threshold epoch timestamp in milliseconds before which workers are considered stale
     * @return a future completing when stale workers have been pruned
     */
    Future<Void> pruneStaleWorkers(long staleThresholdEpoch);
}


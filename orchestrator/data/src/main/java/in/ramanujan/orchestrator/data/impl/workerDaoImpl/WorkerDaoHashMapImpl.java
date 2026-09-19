package in.ramanujan.orchestrator.data.impl.workerDaoImpl;

import in.ramanujan.orchestrator.data.dao.WorkerDao;
import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import in.ramanujan.utils.loadbalancing.LoadBalancerStrategy;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link WorkerDao} using concurrent data structures.
 * <p>
 * Intended for unit testing, integration tests, or single-node developer workflows where
 * an external SQL database is not configured.
 */
public class WorkerDaoHashMapImpl implements WorkerDao {

    private final Map<String, WorkerTelemetry> telemetryMap = new ConcurrentHashMap<>();
    private final Map<String, String> statusMap = new ConcurrentHashMap<>();


    @Override
    public Future<Void> upsertWorkerTelemetry(WorkerTelemetry telemetry) {
        if (telemetry != null && telemetry.getHostId() != null) {
            telemetryMap.put(telemetry.getHostId(), telemetry);
            statusMap.putIfAbsent(telemetry.getHostId(), "OPEN");
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> updateWorkerStatus(String hostId, String status) {
        if (hostId != null && status != null) {
            statusMap.put(hostId, status);
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<List<WorkerTelemetry>> getEligibleWorkers(TaskComplexityProfile complexity, long minPingEpoch) {
        List<WorkerTelemetry> eligible = new ArrayList<>();
        for (Map.Entry<String, WorkerTelemetry> entry : telemetryMap.entrySet()) {
            String hostId = entry.getKey();
            WorkerTelemetry telemetry = entry.getValue();

            // Must be in OPEN status
            String status = statusMap.getOrDefault(hostId, "OPEN");
            if (!"OPEN".equalsIgnoreCase(status)) {
                continue;
            }

            // Must not be stale
            if (telemetry.getLastPingTimestamp() != null && telemetry.getLastPingTimestamp() < minPingEpoch) {
                continue;
            }

            // Must be resource eligible
            if (LoadBalancerStrategy.isEligible(telemetry, complexity)) {
                eligible.add(telemetry);
            }
        }
        return Future.succeededFuture(eligible);
    }

    @Override
    public Future<WorkerTelemetry> getWorker(String hostId) {
        return Future.succeededFuture(telemetryMap.get(hostId));
    }

    @Override
    public Future<Void> pruneStaleWorkers(long staleThresholdEpoch) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, WorkerTelemetry> entry : telemetryMap.entrySet()) {
            if (entry.getValue().getLastPingTimestamp() != null && entry.getValue().getLastPingTimestamp() < staleThresholdEpoch) {
                toRemove.add(entry.getKey());
            }
        }
        for (String id : toRemove) {
            telemetryMap.remove(id);
            statusMap.remove(id);
        }
        return Future.succeededFuture();
    }
}

package in.ramanujan.orchestrator.data.impl.workerDaoImpl;

import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.WorkerNode;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.orchestrator.data.dao.WorkerDao;
import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import in.ramanujan.utils.loadbalancing.LoadBalancerStrategy;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL-backed implementation of {@link WorkerDao} utilizing the Ramanujan {@link QueryExecutor}.
 * <p>
 * Persists worker capability and real-time telemetry into the {@code workerNode} database table,
 * and queries eligible worker nodes based on status and heartbeat recency for task allocation.
 */
@Component
public class WorkerDaoSqlImpl implements WorkerDao {

    @Autowired
    private QueryExecutor queryExecutor;

    private static final Logger logger = LoggerFactory.getLogger(WorkerDaoSqlImpl.class);


    @Override
    public Future<Void> upsertWorkerTelemetry(WorkerTelemetry telemetry) {
        Future<Void> future = Future.future();
        if (telemetry == null || telemetry.getHostId() == null) {
            future.complete();
            return future;
        }

        try {
            WorkerNode node = toWorkerNode(telemetry, WorkerNode.Status.OPEN.getValue());
            queryExecutor.execute(node, null, QueryType.UPSERT).setHandler(handler -> {
                if (handler.succeeded()) {
                    future.complete();
                } else {
                    logger.error("Failed to upsert worker telemetry for " + telemetry.getHostId(), handler.cause());
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> updateWorkerStatus(String hostId, String status) {
        Future<Void> future = Future.future();
        if (hostId == null) {
            future.complete();
            return future;
        }

        try {
            WorkerNode node = new WorkerNode();
            node.setHostId(hostId);
            node.setStatus(status);

            queryExecutor.execute(node, Keys.HOST_ID, QueryType.UPDATE).setHandler(handler -> {
                if (handler.succeeded()) {
                    future.complete();
                } else {
                    logger.error("Failed to update status for host " + hostId, handler.cause());
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<List<WorkerTelemetry>> getEligibleWorkers(TaskComplexityProfile complexity, long minPingEpoch) {
        Future<List<WorkerTelemetry>> future = Future.future();
        try {
            WorkerNode queryNode = new WorkerNode();
            queryNode.setStatus(WorkerNode.Status.OPEN.getValue());
            queryNode.setLastPing(minPingEpoch);

            queryExecutor.execute(queryNode, Keys.STATUS_LAST_PING, QueryType.SELECT).setHandler(handler -> {
                if (handler.succeeded()) {
                    List<WorkerTelemetry> eligible = new ArrayList<>();
                    if (handler.result() != null) {
                        for (Object obj : handler.result()) {
                            if (obj instanceof WorkerNode) {
                                WorkerTelemetry telemetry = toWorkerTelemetry((WorkerNode) obj);
                                if (LoadBalancerStrategy.isEligible(telemetry, complexity)) {
                                    eligible.add(telemetry);
                                }
                            }
                        }
                    }
                    future.complete(eligible);
                } else {
                    logger.error("Failed to select eligible workers from DB", handler.cause());
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<WorkerTelemetry> getWorker(String hostId) {
        Future<WorkerTelemetry> future = Future.future();
        try {
            WorkerNode queryNode = new WorkerNode();
            queryNode.setHostId(hostId);

            queryExecutor.execute(queryNode, Keys.HOST_ID, QueryType.SELECT).setHandler(handler -> {
                if (handler.succeeded()) {
                    if (handler.result() != null && !handler.result().isEmpty()) {
                        WorkerNode node = (WorkerNode) handler.result().get(0);
                        future.complete(toWorkerTelemetry(node));
                    } else {
                        future.complete(null);
                    }
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> pruneStaleWorkers(long staleThresholdEpoch) {
        // Can optionally set status = OFFLINE for stale workers
        return Future.succeededFuture();
    }

    private WorkerNode toWorkerNode(WorkerTelemetry telemetry, String status) {
        WorkerNode node = new WorkerNode();
        node.setHostId(telemetry.getHostId());
        node.setStatus(status);
        node.setCapabilityRank(telemetry.getCapabilityRank() != null ? telemetry.getCapabilityRank() : 1.0);
        node.setAvailableThreads(telemetry.getAvailableThreads() != null ? telemetry.getAvailableThreads() : 1);
        node.setTotalThreads(telemetry.getTotalThreads() != null ? telemetry.getTotalThreads() : 1);
        node.setAvailableRamMb(telemetry.getAvailableRamMb() != null ? telemetry.getAvailableRamMb() : 512L);
        node.setTotalRamMb(telemetry.getTotalRamMb() != null ? telemetry.getTotalRamMb() : 1024L);
        node.setHasGpu(Boolean.TRUE.equals(telemetry.getHasGpu()) ? 1 : 0);
        node.setDeviceType(telemetry.getDeviceType() != null ? telemetry.getDeviceType() : "GENERIC");
        node.setLastPing(telemetry.getLastPingTimestamp() != null ? telemetry.getLastPingTimestamp() : System.currentTimeMillis());
        return node;
    }

    private WorkerTelemetry toWorkerTelemetry(WorkerNode node) {
        return WorkerTelemetry.builder()
                .hostId(node.getHostId())
                .capabilityRank(node.getCapabilityRank() != null ? node.getCapabilityRank() : 1.0)
                .availableThreads(node.getAvailableThreads() != null ? node.getAvailableThreads() : 1)
                .totalThreads(node.getTotalThreads() != null ? node.getTotalThreads() : 1)
                .availableRamMb(node.getAvailableRamMb() != null ? node.getAvailableRamMb() : 512L)
                .totalRamMb(node.getTotalRamMb() != null ? node.getTotalRamMb() : 1024L)
                .hasGpu(node.getHasGpu() != null && node.getHasGpu() == 1)
                .deviceType(node.getDeviceType() != null ? node.getDeviceType() : "GENERIC")
                .lastPingTimestamp(node.getLastPing())
                .build();
    }
}

package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.HostsDao;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service handling worker open ping / check-in requests.
 * <p>
 * Registers the pinging worker host with dynamic telemetry in the scheduling pool and retrieves
 * any queued task assigned to this worker.
 */
@Component
public class OpenPingService {

    @Autowired
    private HostsDao hostsDao;

    /**
     * Stores a worker ping using baseline default telemetry.
     *
     * @param hostId the unique identifier of the worker host
     * @return future containing an assigned task if available, or null
     */
    public Future<AsyncTask> storePing(String hostId) {
        return storePing(hostId, WorkerTelemetry.createDefault(hostId));
    }

    /**
     * Stores a worker ping along with real-time capability and resource telemetry.
     *
     * @param hostId    the unique identifier of the worker host
     * @param telemetry real-time telemetry reported by the worker
     * @return future containing an assigned task if available, or null
     */
    public Future<AsyncTask> storePing(String hostId, WorkerTelemetry telemetry) {
        Future<AsyncTask> future = Future.future();
        hostsDao.putMachineForComputation(hostId, telemetry).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete(handler.result());
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}


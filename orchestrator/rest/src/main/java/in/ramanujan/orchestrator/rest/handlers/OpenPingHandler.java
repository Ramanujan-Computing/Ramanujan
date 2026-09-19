package in.ramanujan.orchestrator.rest.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.ramanujan.orchestrator.service.OpenPingService;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * HTTP handler for worker open ping requests ({@code /open}).
 * <p>
 * Extracts real-time worker telemetry (available threads, free RAM, capability rank, GPU support)
 * from the request body JSON or URL query parameters, passes it to {@link OpenPingService},
 * and returns the assigned {@link AsyncTask} or null if idle.
 */
@Component
public class OpenPingHandler implements Handler<RoutingContext> {

    @Autowired
    private OpenPingService openPingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handles the routing context for incoming worker open ping requests.
     *
     * @param event the Vert.x routing context
     */
    @Override
    public void handle(RoutingContext event) {
        String hostId = event.queryParams().get("uuid");
        WorkerTelemetry telemetry = extractTelemetry(event, hostId);
        openPingService.storePing(hostId, telemetry).setHandler(new MonitoringHandler<>("openPing", handler -> {
            if(handler.succeeded()) {
                ApiResponse apiResponse = new ApiResponse(Status.SUCCESS.getKeyName(), handler.result());
                try {
                    String responseStr = objectMapper.writeValueAsString(apiResponse);
                    event.response().setStatusCode(HttpResponseStatus.OK.code()).end(responseStr);
                } catch (Exception e) {
                    apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), e);
                    event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                            JsonObject.mapFrom(apiResponse).toString());
                }
            } else {
                ApiResponse apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), handler.cause());
                event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
                        JsonObject.mapFrom(apiResponse).toString()
                );
            }
        }));
    }

    /**
     * Extracts worker telemetry from either the request body JSON payload or query parameters,
     * falling back to safe defaults if not present.
     *
     * @param event  the HTTP routing context
     * @param hostId the unique identifier of the worker host
     * @return populated {@link WorkerTelemetry}
     */
    private WorkerTelemetry extractTelemetry(RoutingContext event, String hostId) {

        WorkerTelemetry telemetry = WorkerTelemetry.createDefault(hostId);
        try {
            String body = event.getBodyAsString();
            if (body != null && !body.trim().isEmpty() && body.trim().startsWith("{")) {
                telemetry = objectMapper.readValue(body, WorkerTelemetry.class);
                if (telemetry.getHostId() == null) {
                    telemetry.setHostId(hostId);
                }
                if (telemetry.getLastPingTimestamp() == null) {
                    telemetry.setLastPingTimestamp(System.currentTimeMillis());
                }
                return telemetry;
            }
        } catch (Exception ignored) {}

        try {
            if (event.queryParams().contains("threads")) {
                telemetry.setAvailableThreads(Integer.parseInt(event.queryParams().get("threads")));
            }
            if (event.queryParams().contains("ramMb")) {
                telemetry.setAvailableRamMb(Long.parseLong(event.queryParams().get("ramMb")));
            }
            if (event.queryParams().contains("totalThreads")) {
                telemetry.setTotalThreads(Integer.parseInt(event.queryParams().get("totalThreads")));
            }
            if (event.queryParams().contains("totalRamMb")) {
                telemetry.setTotalRamMb(Long.parseLong(event.queryParams().get("totalRamMb")));
            }
            if (event.queryParams().contains("rank")) {
                telemetry.setCapabilityRank(Double.parseDouble(event.queryParams().get("rank")));
            }
            if (event.queryParams().contains("gpu")) {
                telemetry.setHasGpu(Boolean.parseBoolean(event.queryParams().get("gpu")));
            }
            if (event.queryParams().contains("deviceType")) {
                telemetry.setDeviceType(event.queryParams().get("deviceType"));
            }
        } catch (Exception ignored) {}
        telemetry.setLastPingTimestamp(System.currentTimeMillis());
        return telemetry;
    }
}

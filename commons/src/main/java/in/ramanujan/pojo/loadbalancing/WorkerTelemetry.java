package in.ramanujan.pojo.loadbalancing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Data transfer and persistence model representing real-time telemetry and hardware capabilities
 * of a worker node in the Ramanujan distributed compute network.
 *
 * <p>Worker nodes report this information to the orchestrator or homelab server during every
 * {@code /pings/open} long-poll and {@code /pings/heartbeat} call. Dynamic metrics (such as
 * {@link #availableThreads} and {@link #availableRamMb}) can fluctuate between pings depending
 * on system background activity, memory pressure, and garbage collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkerTelemetry implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier (UUID) assigned to this worker host instance.
     */
    private String hostId;

    /**
     * Normalized overall capability ranking of the worker machine, typically on a scale from
     * 1.0 (low-end phone) to 10.0 (high-end GPU workstation/server).
     */
    @Builder.Default
    private Double capabilityRank = 1.0;

    /**
     * Number of dynamic CPU execution threads currently available to take on new compute tasks.
     */
    @Builder.Default
    private Integer availableThreads = 1;

    /**
     * Total physical or logical CPU threads/cores present on the worker machine.
     */
    @Builder.Default
    private Integer totalThreads = 1;

    /**
     * Currently free/available system or JVM RAM in megabytes (MB). Used by the load balancer
     * to prevent assigning memory-intensive tasks that would trigger Out-Of-Memory (OOM) crashes.
     */
    @Builder.Default
    private Long availableRamMb = 512L;

    /**
     * Total configured system or JVM RAM in megabytes (MB).
     */
    @Builder.Default
    private Long totalRamMb = 1024L;

    /**
     * Indicates whether the worker environment has an active GPU acceleration runtime
     * (e.g. OpenCL, Metal, CUDA) available for executing native GPU kernels.
     */
    @Builder.Default
    private Boolean hasGpu = false;

    /**
     * Classification of the host device (e.g. "ANDROID", "DESKTOP", "MAC", "LINUX_SERVER").
     */
    @Builder.Default
    private String deviceType = "GENERIC";

    /**
     * Epoch timestamp in milliseconds when this telemetry snapshot was generated or received.
     */
    private Long lastPingTimestamp;

    /**
     * Creates a default telemetry record for a worker with safe, conservative baseline values.
     * Used as a fallback when legacy or third-party workers connect without sending dynamic metrics.
     *
     * @param hostId the unique UUID of the worker host
     * @return a safe baseline {@link WorkerTelemetry} instance
     */
    public static WorkerTelemetry createDefault(String hostId) {
        return WorkerTelemetry.builder()
                .hostId(hostId)
                .capabilityRank(1.0)
                .availableThreads(1)
                .totalThreads(1)
                .availableRamMb(512L)
                .totalRamMb(1024L)
                .hasGpu(false)
                .deviceType("GENERIC")
                .lastPingTimestamp(System.currentTimeMillis())
                .build();
    }
}

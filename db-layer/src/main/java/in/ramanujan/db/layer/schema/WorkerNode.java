package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.Operation;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.constants.OperationName;
import lombok.Data;

/**
 * Database entity mapping for the {@code workerNode} table.
 * <p>
 * Stores static capability attributes and real-time telemetry metrics reported by workers,
 * including dynamic available CPU threads, free RAM, capability rank, GPU availability, and status.
 */
@Data
@Table("workerNode")
public class WorkerNode {

    /**
     * Unique identifier for the worker host machine or device.
     */
    @PrimaryKey(keyValue = Keys.HOST_ID, order = "1")
    @ColumnName("hostId")
    private String hostId;

    /**
     * Current availability status of the worker (e.g., OPEN, ENGAGED, OFFLINE).
     */
    @PrimaryKey(keyValue = Keys.STATUS_LAST_PING, order = "1")
    @ColumnName("status")
    private String status;

    /**
     * Timestamp (in milliseconds epoch) of the last heartbeat or ping received from this worker.
     */
    @PrimaryKey(keyValue = Keys.STATUS_LAST_PING, order = "2")
    @Operation(OperationName.GREATER_THAN_EQUAL_TO)
    @ColumnName("lastPing")
    private Long lastPing;

    /**
     * Relative capability score / performance tier ranking of the worker (higher indicates more computational power).
     */
    @ColumnName("capabilityRank")
    private Double capabilityRank;

    /**
     * Current count of dynamically available CPU processing threads reported by the worker.
     */
    @ColumnName("availableThreads")
    private Integer availableThreads;

    /**
     * Total hardware CPU cores or hardware execution threads on the worker machine.
     */
    @ColumnName("totalThreads")
    private Integer totalThreads;

    /**
     * Real-time free / available physical memory in megabytes reported by the worker.
     */
    @ColumnName("availableRamMb")
    private Long availableRamMb;

    /**
     * Total physical memory in megabytes installed on the worker machine.
     */
    @ColumnName("totalRamMb")
    private Long totalRamMb;

    /**
     * Flag indicating whether GPU compute capabilities are present and accessible (1 for true, 0 for false).
     */
    @ColumnName("hasGpu")
    private Integer hasGpu; // 1 for true, 0 for false

    /**
     * Architectural device category or platform type (e.g., SERVER, LAPTOP, ANDROID, DESKTOP).
     */
    @ColumnName("deviceType")
    private String deviceType;

    /**
     * Operating statuses of a worker node in the cluster.
     */
    public enum Status {
        /**
         * Worker is idle and actively waiting for task assignments.
         */
        OPEN("OPEN"),

        /**
         * Worker is currently running an assigned computational task.
         */
        ENGAGED("ENGAGED"),

        /**
         * Worker has timed out or disconnected.
         */
        OFFLINE("OFFLINE");

        private final String value;

        /**
         * Constructs a status enum with the specified database string value.
         *
         * @param value the string representation of the status
         */
        Status(String value) {
            this.value = value;
        }

        /**
         * Gets the database column string value for this status.
         *
         * @return status string representation
         */
        public String getValue() {
            return value;
        }
    }
}


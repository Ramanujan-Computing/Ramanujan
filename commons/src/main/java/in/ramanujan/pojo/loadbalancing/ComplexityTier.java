package in.ramanujan.pojo.loadbalancing;

/**
 * Enumeration of task complexity tiers used by the load-balancing and scheduling system.
 *
 * <p>Categorizing tasks into distinct tiers allows the scheduler to match compute-intensive
 * and memory-heavy workloads with powerful worker machines (e.g. servers or workstations),
 * while preserving low-powered workers (e.g. phones or low-spec devices) for lightweight tasks.
 */
public enum ComplexityTier {

    /**
     * Minimal compute and memory footprint (e.g., scalar operations, variable assignments,
     * small arrays < 128 MB, no iterative while-loops). Suitable for execution on any device.
     */
    LIGHT,

    /**
     * Moderate computation or memory footprint (e.g., arrays between 128 MB and 1024 MB,
     * single-loop iterations, moderate number of operations). Requires multi-core execution.
     */
    MEDIUM,

    /**
     * Intensive workloads requiring significant memory (>= 1024 MB) or extensive iteration
     * (multiple while-loops, large matrix computations). Must be routed to high-RAM, high-core workers.
     */
    HEAVY,

    /**
     * Workloads containing OpenCL GPU kernel invocations (e.g., ray tracing, CFD lattice Boltzmann,
     * matrix multiplications ending in "_GPU"). Requires workers equipped with a GPU runtime bridge.
     */
    GPU_ACCELERATED
}

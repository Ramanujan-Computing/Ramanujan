package in.ramanujan.pojo.loadbalancing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Encapsulates the estimated resource demands and computational characteristics of an executable task
 * (e.g. an individual {@link in.ramanujan.translation.codeConverter.DagElement} or
 * {@link in.ramanujan.orchestrator.base.pojo.AsyncTask}).
 *
 * <p>Constructed by {@link in.ramanujan.utils.loadbalancing.TaskComplexityAnalyzer} by examining
 * declared arrays, memory buffer sizes, loop structures, and GPU function markers in the task's
 * {@link in.ramanujan.pojo.RuleEngineInput}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskComplexityProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Estimated minimum RAM in megabytes (MB) required to safely load arrays and execute the task
     * without running out of memory. Includes a 30% safety margin for runtime allocations.
     */
    @Builder.Default
    private Long requiredRamMb = 64L;

    /**
     * Estimated compute intensity score combining scalar commands, operations, while-loop
     * iterations, and function/kernel invocations.
     */
    @Builder.Default
    private Double computeScore = 1.0;

    /**
     * Minimum number of concurrent CPU threads strictly required by this task to execute.
     */
    @Builder.Default
    private Integer minThreads = 1;

    /**
     * Ideal number of CPU threads recommended for maximum parallel efficiency when executing this task.
     */
    @Builder.Default
    private Integer preferredThreads = 1;

    /**
     * Set to {@code true} if the task includes OpenCL C GPU kernels (e.g. methods ending in "_GPU").
     */
    @Builder.Default
    private Boolean requiresGpu = false;

    /**
     * High-level classification tier of this task ({@link ComplexityTier#LIGHT},
     * {@link ComplexityTier#MEDIUM}, {@link ComplexityTier#HEAVY}, or {@link ComplexityTier#GPU_ACCELERATED}).
     */
    @Builder.Default
    private ComplexityTier complexityTier = ComplexityTier.LIGHT;

    /**
     * Produces a default {@link TaskComplexityProfile} suitable for simple scalar tasks
     * or when static code analysis cannot be performed.
     *
     * @return a baseline {@link TaskComplexityProfile}
     */
    public static TaskComplexityProfile defaultLight() {
        return TaskComplexityProfile.builder()
                .requiredRamMb(64L)
                .computeScore(10.0)
                .minThreads(1)
                .preferredThreads(1)
                .requiresGpu(false)
                .complexityTier(ComplexityTier.LIGHT)
                .build();
    }
}

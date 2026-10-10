package in.ramanujan.utils.loadbalancing;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.loadbalancing.ComplexityTier;
import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;

import java.io.File;
import java.util.List;

/**
 * Static analyzer that inspects a {@link RuleEngineInput} AST representation of a task to compute
 * its expected RAM requirement and computational complexity score.
 *
 * <p>The analysis proceeds across three dimensions:
 * <ul>
 *   <li><b>Memory Footprint:</b> Computes the total storage size of all declared array structures,
 *       multiplying dimension sizes by data type byte widths, factoring in binary array backing files,
 *       and applying a 30% safety multiplier plus a 32 MB baseline.</li>
 *   <li><b>Compute Score:</b> Evaluates instruction counts (commands, arithmetic/logical operations,
 *       conditions, branches), with exponential scaling for while-loop iterations and weights for
 *       function invocations.</li>
 *   <li><b>Hardware Demands:</b> Detects whether the task contains GPU-accelerated OpenCL kernels
 *       and recommends minimum and preferred CPU thread counts.</li>
 * </ul>
 */
public class TaskComplexityAnalyzer {

    /** Baseline memory overhead reserved for runtime JVM structures and object graphs (32 MB). */
    private static final long BASELINE_MEMORY_BYTES = 32L * 1024L * 1024L;

    /** Safety buffer applied to total array memory to accommodate working variables and serialization overhead. */
    private static final double MEMORY_SAFETY_MARGIN = 1.30;

    /**
     * Analyzes a given {@link RuleEngineInput} to produce its {@link TaskComplexityProfile}.
     *
     * @param rei the rule engine input containing the AST commands, arrays, and functions to inspect;
     *            if null, returns a safe light-tier default profile
     * @return a {@link TaskComplexityProfile} capturing RAM in MB, compute score, threading, and GPU requirements
     */
    public static TaskComplexityProfile analyze(RuleEngineInput rei) {
        if (rei == null) {
            return TaskComplexityProfile.defaultLight();
        }

        // 1. Calculate Memory Requirements from Arrays
        long totalArrayBytes = 0L;
        if (rei.getArrays() != null) {
            for (Array array : rei.getArrays()) {
                totalArrayBytes += estimateArrayMemoryBytes(array);
            }
        }

        long requiredRamBytes = BASELINE_MEMORY_BYTES + (long) (totalArrayBytes * MEMORY_SAFETY_MARGIN);
        long requiredRamMb = Math.max(32L, (long) Math.ceil((double) requiredRamBytes / (1024.0 * 1024.0)));

        // 2. Calculate Compute Complexity Score
        int commandCount = rei.getCommands() != null ? rei.getCommands().size() : 0;
        int opCount = rei.getOperations() != null ? rei.getOperations().size() : 0;
        int condCount = rei.getConditions() != null ? rei.getConditions().size() : 0;
        int ifCount = rei.getIfBlocks() != null ? rei.getIfBlocks().size() : 0;
        int whileCount = rei.getWhileBlocks() != null ? rei.getWhileBlocks().size() : 0;
        int funcCount = rei.getFunctionCalls() != null ? rei.getFunctionCalls().size() : 0;

        boolean requiresGpu = false;
        int gpuKernelCount = 0;
        if (rei.getFunctionCalls() != null) {
            for (FunctionCall fc : rei.getFunctionCalls()) {
                if (Boolean.TRUE.equals(fc.getIsGpu())) {
                    requiresGpu = true;
                    gpuKernelCount++;
                }
            }
        }

        // Compute score estimation:
        // Base instructions + loop weight factor + function calls + GPU factor
        double computeScore = commandCount + (opCount * 1.5) + (condCount * 1.2) + (ifCount * 1.5);
        if (whileCount > 0) {
            // Loops execute repeatedly; scale non-linearly with while blocks
            computeScore += (whileCount * 1500.0) + (computeScore * Math.pow(2.0, Math.min(whileCount, 6)));
        }
        computeScore += (funcCount * 100.0);
        if (gpuKernelCount > 0) {
            computeScore += (gpuKernelCount * 5000.0);
        }

        // 3. Determine Complexity Tier and Recommended Threading
        ComplexityTier tier;
        int minThreads = 1;
        int preferredThreads = 1;

        if (requiresGpu) {
            tier = ComplexityTier.GPU_ACCELERATED;
            minThreads = 1;
            preferredThreads = 4;
        } else if (requiredRamMb >= 1024L || computeScore >= 20000.0 || whileCount >= 3) {
            tier = ComplexityTier.HEAVY;
            minThreads = 2;
            preferredThreads = 8;
        } else if (requiredRamMb >= 256L || computeScore >= 1000.0 || whileCount >= 1) {
            tier = ComplexityTier.MEDIUM;
            minThreads = 1;
            preferredThreads = 4;
        } else {
            tier = ComplexityTier.LIGHT;
            minThreads = 1;
            preferredThreads = 1;
        }

        return TaskComplexityProfile.builder()
                .requiredRamMb(requiredRamMb)
                .computeScore(Math.max(1.0, computeScore))
                .minThreads(minThreads)
                .preferredThreads(preferredThreads)
                .requiresGpu(requiresGpu)
                .complexityTier(tier)
                .build();
    }

    /**
     * Estimates the memory footprint of a declared array in bytes based on its backing file,
     * dimensions, or map entries.
     *
     * @param array the {@link Array} entity to evaluate
     * @return estimated memory consumption in bytes
     */
    private static long estimateArrayMemoryBytes(Array array) {
        if (array == null) {
            return 0L;
        }

        // Check if backed by a binary file
        if (array.getBinaryFile() != null && !array.getBinaryFile().isEmpty()) {
            File f = new File(array.getBinaryFile());
            if (f.exists() && f.isFile()) {
                return f.length();
            }
        }

        int bytesPerElement = getElementByteSize(array.getDataType());

        // Check declared dimensions
        List<Integer> dims = array.getDimension();
        if (dims != null && !dims.isEmpty()) {
            long elementCount = 1L;
            for (Integer dim : dims) {
                if (dim != null && dim > 0) {
                    elementCount *= dim;
                }
            }
            return elementCount * bytesPerElement;
        }

        // Fallback: estimate from populated values map
        if (array.getValues() != null && !array.getValues().isEmpty()) {
            // Map overhead in Java is ~32 bytes per entry + value size
            return (long) array.getValues().size() * (bytesPerElement + 32L);
        }

        return 0L;
    }

    /**
     * Resolves the byte width of primitive numeric types.
     *
     * @param dataType string representation of data type (e.g. "float", "double", "int")
     * @return size in bytes (4 for float/int, 8 for double/long, 1 for boolean, default 4)
     */
    private static int getElementByteSize(String dataType) {
        if (dataType == null) return 4; // default float32 / int32
        String dt = dataType.trim().toLowerCase();
        switch (dt) {
            case "double":
            case "float64":
            case "long":
            case "int64":
                return 8;
            case "boolean":
            case "bool":
            case "byte":
                return 1;
            case "short":
            case "int16":
                return 2;
            case "float":
            case "float32":
            case "int":
            case "int32":
            case "integer":
            default:
                return 4;
        }
    }
}

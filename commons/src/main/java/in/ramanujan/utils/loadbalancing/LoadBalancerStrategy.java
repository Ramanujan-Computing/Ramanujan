package in.ramanujan.utils.loadbalancing;

import in.ramanujan.pojo.loadbalancing.ComplexityTier;
import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;

/**
 * Core matchmaking and load-balancing engine for Ramanujan task scheduling.
 *
 * <p>Provides multi-objective evaluation to pair queued or incoming tasks with connected workers:
 * <ol>
 *   <li><b>Hard Eligibility Filtering:</b> Rejects worker-task pairs where the worker lacks sufficient
 *       free RAM, is missing required GPU capabilities, or does not satisfy minimum thread counts.</li>
 *   <li><b>Tier Affinity & Power Matching:</b> Minimizes power-to-workload divergence, matching heavy
 *       tasks to high-rank servers and light tasks to lightweight devices so powerhouse machines remain
 *       available for compute-intensive blocks.</li>
 *   <li><b>Packing Efficiency:</b> Favors workers whose available RAM closely fits the task requirement
 *       to prevent resource fragmentation.</li>
 *   <li><b>Anti-Starvation Aging:</b> Introduces an aging bonus proportional to task wait duration to guarantee
 *       eventual execution for all tasks.</li>
 * </ol>
 */
public class LoadBalancerStrategy {

    /**
     * Determines whether a worker is physically capable of executing a task
     * based on available RAM, available CPU threads, and required accelerators (GPU).
     *
     * @param worker the {@link WorkerTelemetry} reporting the worker's real-time resource state
     * @param task   the {@link TaskComplexityProfile} specifying the task's resource requirements
     * @return {@code true} if the worker can safely execute the task; {@code false} otherwise
     */
    public static boolean isEligible(WorkerTelemetry worker, TaskComplexityProfile task) {
        if (worker == null || task == null) {
            return false;
        }

        // 1. RAM Feasibility Check (Strict guard against Out-Of-Memory)
        long workerRam = worker.getAvailableRamMb() != null ? worker.getAvailableRamMb() : 0L;
        long taskRam = task.getRequiredRamMb() != null ? task.getRequiredRamMb() : 0L;
        if (workerRam < taskRam) {
            return false;
        }

        // 2. Hardware Accelerator (GPU) Check
        if (Boolean.TRUE.equals(task.getRequiresGpu())) {
            if (!Boolean.TRUE.equals(worker.getHasGpu())) {
                return false;
            }
        }

        // 3. CPU Thread Availability Check
        int workerThreads = worker.getAvailableThreads() != null ? worker.getAvailableThreads() : 1;
        int minThreads = task.getMinThreads() != null ? task.getMinThreads() : 1;
        if (workerThreads < minThreads) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the matchmaking affinity score between an eligible worker and a task.
     * Higher scores represent a stronger scheduling preference.
     *
     * @param worker     the candidate {@link WorkerTelemetry}
     * @param task       the candidate {@link TaskComplexityProfile}
     * @param waitTimeMs duration in milliseconds that this task has waited in the queue
     * @return a positive double affinity score if eligible, or -1.0 if ineligible
     */
    public static double calculateAffinityScore(WorkerTelemetry worker, TaskComplexityProfile task, long waitTimeMs) {
        if (!isEligible(worker, task)) {
            return -1.0;
        }

        double workerRank = worker.getCapabilityRank() != null ? worker.getCapabilityRank() : 1.0;
        int workerThreads = worker.getAvailableThreads() != null ? worker.getAvailableThreads() : 1;
        long workerRam = worker.getAvailableRamMb() != null ? worker.getAvailableRamMb() : 512L;

        ComplexityTier tier = task.getComplexityTier() != null ? task.getComplexityTier() : ComplexityTier.LIGHT;

        // Target worker capability score: normalized to ~ 1.0 to 10.0 scale
        double workerPower = (workerRank * 0.5)
                + (Math.min(workerThreads, 16) * 0.3)
                + (Math.min(Math.log(Math.max(64, workerRam)) / Math.log(2) / 2.0, 5.0) * 0.2);

        double tierTargetPower;
        switch (tier) {
            case GPU_ACCELERATED:
                tierTargetPower = 9.0;
                break;
            case HEAVY:
                tierTargetPower = 7.5;
                break;
            case MEDIUM:
                tierTargetPower = 4.0;
                break;
            case LIGHT:
            default:
                tierTargetPower = 1.5;
                break;
        }

        // Tier affinity: penalize power mismatch (e.g. assigning trivial task to monster server)
        double powerDiff = Math.abs(workerPower - tierTargetPower);
        double tierAffinity = 100.0 / (1.0 + powerDiff);

        // Resource fit bonus: packing efficiency
        double ramRatio = (double) task.getRequiredRamMb() / (double) Math.max(1L, workerRam);
        double resourceFit = ramRatio * 20.0; // reward proportional memory sizing

        // Thread alignment bonus
        int preferredThreads = task.getPreferredThreads() != null ? task.getPreferredThreads() : 1;
        double threadAlignment = Math.min((double) workerThreads / (double) preferredThreads, 2.0) * 10.0;

        // Anti-starvation aging bonus (adds 1.5 points per second of waiting)
        double agingBonus = Math.max(0.0, waitTimeMs / 1000.0) * 1.5;

        return tierAffinity + resourceFit + threadAlignment + agingBonus;
    }
}

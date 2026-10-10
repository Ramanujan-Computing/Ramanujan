package in.ramanujan.developer.console.loadbalancing;

import in.ramanujan.developer.console.operationImpl.ExecuteInlineHomelabServer.PendingTask;
import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import in.ramanujan.utils.loadbalancing.LoadBalancerStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Priority task pool that matches ready DAG tasks with requesting workers based on
 * available RAM, CPU threads, capability rankings, and GPU availability.
 */
public class LoadBalancedTaskPool {

    private final List<PendingTask> pendingTasks = new ArrayList<>();
    private final Map<String, WorkerTelemetry> activeWorkers = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition taskAvailable = lock.newCondition();

    /**
     * Enqueues a new pending task into the pool and notifies any waiting workers.
     *
     * @param task the pending task with its complexity profile
     */
    public void addTask(PendingTask task) {
        lock.lock();
        try {
            pendingTasks.add(task);
            taskAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records or updates the latest real-time telemetry reported by a worker.
     *
     * @param telemetry the worker's reported telemetry
     */
    public void recordWorkerTelemetry(WorkerTelemetry telemetry) {
        if (telemetry != null && telemetry.getHostId() != null) {
            activeWorkers.put(telemetry.getHostId(), telemetry);
        }
    }

    /**
     * Polls the pool for the best-matching task for the specified worker, waiting up to {@code timeoutMs}.
     * <p>
     * Matchmaking checks strict resource eligibility (memory, threads, GPU) and scores candidates
     * based on capability fit, RAM headroom, and queue waiting time.
     *
     * @param worker    the telemetry of the requesting worker
     * @param timeoutMs maximum time in milliseconds to wait if no matching task is immediately ready
     * @return the best matched {@link PendingTask}, or {@code null} if the timeout expires without a match
     * @throws InterruptedException if interrupted while waiting
     */
    public PendingTask pollBestMatch(WorkerTelemetry worker, long timeoutMs) throws InterruptedException {
        if (worker == null) {
            worker = WorkerTelemetry.createDefault("unknown");
        }
        recordWorkerTelemetry(worker);

        long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        lock.lock();
        try {
            while (true) {
                PendingTask bestTask = null;
                double bestScore = Double.NEGATIVE_INFINITY;
                int bestIndex = -1;

                long now = System.currentTimeMillis();
                for (int i = 0; i < pendingTasks.size(); i++) {
                    PendingTask task = pendingTasks.get(i);
                    TaskComplexityProfile profile = task.complexityProfile;
                    if (LoadBalancerStrategy.isEligible(worker, profile)) {
                        long waitTimeMs = Math.max(0, now - task.enqueuedAt);
                        double score = LoadBalancerStrategy.calculateAffinityScore(worker, profile, waitTimeMs);
                        if (score > bestScore) {
                            bestScore = score;
                            bestTask = task;
                            bestIndex = i;
                        }
                    }
                }

                if (bestTask != null) {
                    pendingTasks.remove(bestIndex);
                    return bestTask;
                }

                if (nanosTimeout <= 0) {
                    return null;
                }

                nanosTimeout = taskAvailable.awaitNanos(nanosTimeout);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current number of pending tasks in the pool.
     *
     * @return number of tasks waiting for execution
     */
    public int size() {
        lock.lock();
        try {
            return pendingTasks.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all pending tasks from the pool.
     */
    public void clear() {
        lock.lock();
        try {
            pendingTasks.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an unmodifiable or live map of all active workers and their latest telemetry.
     *
     * @return map of host ID to worker telemetry
     */
    public Map<String, WorkerTelemetry> getActiveWorkers() {
        return activeWorkers;
    }
}


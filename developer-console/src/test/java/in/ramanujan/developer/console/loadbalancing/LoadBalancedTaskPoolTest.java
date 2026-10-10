package in.ramanujan.developer.console.loadbalancing;

import in.ramanujan.developer.console.operationImpl.ExecuteInlineHomelabServer.PendingTask;
import in.ramanujan.pojo.loadbalancing.ComplexityTier;
import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import org.junit.Assert;
import org.junit.Test;

public class LoadBalancedTaskPoolTest {

    @Test
    public void testTaskPoolMatchesOptimalWorker() throws Exception {
        LoadBalancedTaskPool pool = new LoadBalancedTaskPool();

        TaskComplexityProfile lightProfile = TaskComplexityProfile.builder()
                .requiredRamMb(32L)
                .computeScore(10.0)
                .complexityTier(ComplexityTier.LIGHT)
                .minThreads(1)
                .preferredThreads(1)
                .requiresGpu(false)
                .build();

        TaskComplexityProfile heavyProfile = TaskComplexityProfile.builder()
                .requiredRamMb(4096L)
                .computeScore(50000.0)
                .complexityTier(ComplexityTier.HEAVY)
                .minThreads(2)
                .preferredThreads(8)
                .requiresGpu(false)
                .build();

        PendingTask lightTask = new PendingTask("light-uuid", null, null, "{\"task\":\"light\"}", lightProfile);
        PendingTask heavyTask = new PendingTask("heavy-uuid", null, null, "{\"task\":\"heavy\"}", heavyProfile);

        // Add both tasks
        pool.addTask(heavyTask);
        pool.addTask(lightTask);
        Assert.assertEquals(2, pool.size());

        // Low-RAM phone connects: 1024MB RAM (not enough for 4096MB heavy task)
        WorkerTelemetry phone = WorkerTelemetry.builder()
                .hostId("phone-node")
                .availableThreads(2)
                .availableRamMb(1024L)
                .capabilityRank(2.0)
                .hasGpu(false)
                .build();

        PendingTask phoneTask = pool.pollBestMatch(phone, 100);
        Assert.assertNotNull("Phone should get a matching task", phoneTask);
        Assert.assertEquals("Phone must get light-uuid because heavy task requires 4096MB RAM",
                "light-uuid", phoneTask.uuid);
        Assert.assertEquals(1, pool.size());

        // High-end workstation connects: 16384MB RAM, 16 threads
        WorkerTelemetry workstation = WorkerTelemetry.builder()
                .hostId("workstation-node")
                .availableThreads(16)
                .availableRamMb(16384L)
                .capabilityRank(8.0)
                .hasGpu(false)
                .build();

        PendingTask workstationTask = pool.pollBestMatch(workstation, 100);
        Assert.assertNotNull("Workstation should get heavy task", workstationTask);
        Assert.assertEquals("heavy-uuid", workstationTask.uuid);
        Assert.assertEquals(0, pool.size());
    }

    @Test
    public void testTaskPoolRejectsIneligibleWorkers() throws Exception {
        LoadBalancedTaskPool pool = new LoadBalancedTaskPool();

        TaskComplexityProfile gpuProfile = TaskComplexityProfile.builder()
                .requiredRamMb(128L)
                .computeScore(5000.0)
                .complexityTier(ComplexityTier.GPU_ACCELERATED)
                .minThreads(1)
                .preferredThreads(4)
                .requiresGpu(true)
                .build();

        PendingTask gpuTask = new PendingTask("gpu-uuid", null, null, "{\"task\":\"gpu\"}", gpuProfile);
        pool.addTask(gpuTask);

        // Worker without GPU connects
        WorkerTelemetry cpuOnlyWorker = WorkerTelemetry.builder()
                .hostId("cpu-only")
                .availableThreads(8)
                .availableRamMb(8192L)
                .hasGpu(false)
                .build();

        PendingTask resultForCpu = pool.pollBestMatch(cpuOnlyWorker, 50);
        Assert.assertNull("CPU-only worker must not be assigned a GPU-accelerated task", resultForCpu);
        Assert.assertEquals("Task should remain in pool", 1, pool.size());

        // Worker with GPU connects
        WorkerTelemetry gpuWorker = WorkerTelemetry.builder()
                .hostId("gpu-node")
                .availableThreads(8)
                .availableRamMb(8192L)
                .hasGpu(true)
                .build();

        PendingTask resultForGpu = pool.pollBestMatch(gpuWorker, 50);
        Assert.assertNotNull("GPU worker should successfully receive GPU task", resultForGpu);
        Assert.assertEquals("gpu-uuid", resultForGpu.uuid);
        Assert.assertEquals(0, pool.size());
    }
}

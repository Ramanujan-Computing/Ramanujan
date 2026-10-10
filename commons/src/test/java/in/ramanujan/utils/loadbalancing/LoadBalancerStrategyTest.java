package in.ramanujan.utils.loadbalancing;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.loadbalancing.ComplexityTier;
import in.ramanujan.pojo.loadbalancing.TaskComplexityProfile;
import in.ramanujan.pojo.loadbalancing.WorkerTelemetry;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.While;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class LoadBalancerStrategyTest {

    @Test
    public void testTaskComplexityAnalyzerScalarTask() {
        RuleEngineInput rei = new RuleEngineInput();
        Command cmd = new Command();
        cmd.setId("cmd1");
        rei.setCommands(Collections.singletonList(cmd));

        TaskComplexityProfile profile = TaskComplexityAnalyzer.analyze(rei);
        Assert.assertEquals(ComplexityTier.LIGHT, profile.getComplexityTier());
        Assert.assertFalse(profile.getRequiresGpu());
        Assert.assertTrue(profile.getRequiredRamMb() >= 32L && profile.getRequiredRamMb() < 100L);
    }

    @Test
    public void testTaskComplexityAnalyzerHeavyArrayTask() {
        RuleEngineInput rei = new RuleEngineInput();
        Array bigArray = new Array();
        bigArray.setName("matrix");
        bigArray.setDataType("float");
        // 1000 x 1000 float array = 1,000,000 floats * 4 bytes = 4,000,000 bytes (~4 MB)
        bigArray.setDimension(Arrays.asList(1000, 1000));
        rei.setArrays(Collections.singletonList(bigArray));

        While whileBlock = new While();
        rei.setWhileBlocks(Collections.singletonList(whileBlock));

        TaskComplexityProfile profile = TaskComplexityAnalyzer.analyze(rei);
        Assert.assertTrue("Profile should be MEDIUM or HEAVY",
                profile.getComplexityTier() == ComplexityTier.MEDIUM || profile.getComplexityTier() == ComplexityTier.HEAVY);
        Assert.assertTrue("Compute score should be high due to while loop", profile.getComputeScore() > 1000.0);
    }

    @Test
    public void testTaskComplexityAnalyzerGpuTask() {
        RuleEngineInput rei = new RuleEngineInput();
        FunctionCall gpuFunc = new FunctionCall();
        gpuFunc.setIsGpu(true);
        gpuFunc.setId("ray_trace_GPU");
        rei.setFunctionCalls(Collections.singletonList(gpuFunc));

        TaskComplexityProfile profile = TaskComplexityAnalyzer.analyze(rei);
        Assert.assertEquals(ComplexityTier.GPU_ACCELERATED, profile.getComplexityTier());
        Assert.assertTrue(profile.getRequiresGpu());
    }

    @Test
    public void testWorkerEligibilityRamCheck() {
        TaskComplexityProfile heavyMemoryTask = TaskComplexityProfile.builder()
                .requiredRamMb(2048L)
                .complexityTier(ComplexityTier.HEAVY)
                .minThreads(1)
                .requiresGpu(false)
                .build();

        WorkerTelemetry lowRamWorker = WorkerTelemetry.builder()
                .hostId("phone-1")
                .availableRamMb(512L)
                .availableThreads(4)
                .hasGpu(false)
                .build();

        WorkerTelemetry highRamWorker = WorkerTelemetry.builder()
                .hostId("server-1")
                .availableRamMb(8192L)
                .availableThreads(16)
                .hasGpu(false)
                .build();

        Assert.assertFalse("Low RAM worker should be rejected",
                LoadBalancerStrategy.isEligible(lowRamWorker, heavyMemoryTask));
        Assert.assertTrue("High RAM worker should be accepted",
                LoadBalancerStrategy.isEligible(highRamWorker, heavyMemoryTask));
    }

    @Test
    public void testWorkerEligibilityGpuCheck() {
        TaskComplexityProfile gpuTask = TaskComplexityProfile.builder()
                .requiredRamMb(128L)
                .complexityTier(ComplexityTier.GPU_ACCELERATED)
                .minThreads(1)
                .requiresGpu(true)
                .build();

        WorkerTelemetry cpuWorker = WorkerTelemetry.builder()
                .hostId("cpu-node")
                .availableRamMb(4096L)
                .availableThreads(8)
                .hasGpu(false)
                .build();

        WorkerTelemetry gpuWorker = WorkerTelemetry.builder()
                .hostId("gpu-node")
                .availableRamMb(4096L)
                .availableThreads(8)
                .hasGpu(true)
                .build();

        Assert.assertFalse("Worker without GPU should be rejected for GPU task",
                LoadBalancerStrategy.isEligible(cpuWorker, gpuTask));
        Assert.assertTrue("Worker with GPU should be accepted for GPU task",
                LoadBalancerStrategy.isEligible(gpuWorker, gpuTask));
    }

    @Test
    public void testAffinityMatchingHeavyVsLightWorker() {
        TaskComplexityProfile lightTask = TaskComplexityProfile.builder()
                .requiredRamMb(64L)
                .computeScore(10.0)
                .complexityTier(ComplexityTier.LIGHT)
                .preferredThreads(1)
                .minThreads(1)
                .requiresGpu(false)
                .build();

        TaskComplexityProfile heavyTask = TaskComplexityProfile.builder()
                .requiredRamMb(2048L)
                .computeScore(50000.0)
                .complexityTier(ComplexityTier.HEAVY)
                .preferredThreads(8)
                .minThreads(2)
                .requiresGpu(false)
                .build();

        WorkerTelemetry phone = WorkerTelemetry.builder()
                .hostId("phone")
                .capabilityRank(1.5)
                .availableThreads(2)
                .availableRamMb(1024L)
                .hasGpu(false)
                .build();

        WorkerTelemetry server = WorkerTelemetry.builder()
                .hostId("server")
                .capabilityRank(8.0)
                .availableThreads(16)
                .availableRamMb(16384L)
                .hasGpu(false)
                .build();

        double serverOnHeavy = LoadBalancerStrategy.calculateAffinityScore(server, heavyTask, 0);
        double phoneOnHeavy = LoadBalancerStrategy.calculateAffinityScore(phone, heavyTask, 0);
        // Phone has only 1024MB RAM, heavyTask requires 2048MB -> phone is ineligible!
        Assert.assertEquals(-1.0, phoneOnHeavy, 0.001);
        Assert.assertTrue("Server on heavy task should have high score", serverOnHeavy > 50.0);

        // For light task: phone should have a strong affinity score
        double phoneOnLight = LoadBalancerStrategy.calculateAffinityScore(phone, lightTask, 0);
        Assert.assertTrue("Phone should be eligible and scored for light task", phoneOnLight > 0.0);
    }
}

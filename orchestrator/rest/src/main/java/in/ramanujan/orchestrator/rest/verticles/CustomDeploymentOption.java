package in.ramanujan.orchestrator.rest.verticles;

import io.vertx.core.DeploymentOptions;

public class CustomDeploymentOption {
    public DeploymentOptions getDeployOptions(String className, Integer workerThreadPoolSize) {
        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setWorkerPoolName(className + "_workerPool")
                .setWorkerPoolSize(workerThreadPoolSize).setWorker(true);
        return deploymentOptions;
    }
}

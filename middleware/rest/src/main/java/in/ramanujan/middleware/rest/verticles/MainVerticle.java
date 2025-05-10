package in.ramanujan.middleware.rest.verticles;

import in.ramanujan.monitoringutils.verticles.MonitoringVerticle;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import in.ramanujan.middleware.rest.CustomDeploymentOption;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
    Logger logger= LoggerFactory.getLogger(MainVerticle.class);
    CustomDeploymentOption option = new CustomDeploymentOption();
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        ConfigurationGetter.init(config());
        logger.info("deploying application verticles");
        // Manual wiring of dependencies
        // --- DAOs and QueryExecutor ---
        in.ramanujan.db.layer.utils.QueryExecutor queryExecutor = new in.ramanujan.db.layer.utils.QueryExecutor();
        in.ramanujan.data.db.impl.asyncTaskDao.AsyncTaskSqlDbImpl asyncTaskDao = new in.ramanujan.data.db.impl.asyncTaskDao.AsyncTaskSqlDbImpl();
        asyncTaskDao.queryExecutor = queryExecutor;
        in.ramanujan.data.db.impl.VariableValueDao.VariableValueSqlDbImpl variableValueDao = new in.ramanujan.data.db.impl.VariableValueDao.VariableValueSqlDbImpl();
        variableValueDao.queryExecutor = queryExecutor;
        // --- Services ---
        in.ramanujan.middleware.service.TaskStatusService taskStatusService = new in.ramanujan.middleware.service.TaskStatusService();
        taskStatusService.asyncTaskDao = asyncTaskDao;
        taskStatusService.variableValueDao = variableValueDao;
        in.ramanujan.middleware.service.TranslateService translateService = new in.ramanujan.middleware.service.TranslateService();
        in.ramanujan.middleware.service.RunService runService = new in.ramanujan.middleware.service.RunService();
        runService.asyncTaskDao = asyncTaskDao;
        runService.variableValueDao = variableValueDao;
        // --- Handlers ---
        HttpVerticle httpVerticle = new HttpVerticle();
        httpVerticle.translateAndRunHandler = new in.ramanujan.middleware.rest.handler.TranslateAndRunHandler();
        httpVerticle.translateAndRunHandler.translateService = translateService;
        httpVerticle.translateAndRunHandler.runService = runService;
        httpVerticle.statusHandler = new in.ramanujan.middleware.rest.handler.StatusHandler();
        httpVerticle.statusHandler.taskStatusService = taskStatusService;
        httpVerticle.packageRunner = new in.ramanujan.middleware.rest.handler.PackageRunner();
        httpVerticle.workflowSuspendHandler = new in.ramanujan.middleware.rest.handler.WorkflowSuspendHandler();
        httpVerticle.processNextDagElementHandler = new in.ramanujan.middleware.rest.handler.ProcessNextDagElementHandler();
        httpVerticle.orchestrationApiCaller = new in.ramanujan.data.OrchestrationApiCaller();
        httpVerticle.kafkaManagerApiCaller = new in.ramanujan.data.KafkaManagerApiCaller();
        httpVerticle.debugInformationFetchHandler = new in.ramanujan.middleware.rest.handler.DebugInformationFetchHandler();
        httpVerticle.checkpointResumeHandler = new in.ramanujan.middleware.rest.handler.CheckpointResumeHandler();
        httpVerticle.addFirstDebugPointHandler = new in.ramanujan.middleware.rest.handler.AddFirstDebugPointHandler();
        httpVerticle.getDagElementCodeHandler = new in.ramanujan.middleware.rest.handler.GetDagElementCodeHandler();
        httpVerticle.connectionCreator = new in.ramanujan.db.layer.utils.ConnectionCreator();
        httpVerticle.storageDao = new in.ramanujan.data.db.impl.storageDao.StorageDaoGoogleCloudImpl();
        httpVerticle.queryExecutor = queryExecutor;
        vertx.deployVerticle(httpVerticle, option.getDeployOptions("HttpVerticle", 250));
        final String projectId = "ramanujan-340512";
        final MonitoringVerticle monitoringVerticle = new MonitoringVerticle(projectId, "/MetricPusherCred.json", ConfigurationGetter.getMonitoringType());
        vertx.deployVerticle(monitoringVerticle, option.getDeployOptions("MonitoringVerticle", 1));
    }
}

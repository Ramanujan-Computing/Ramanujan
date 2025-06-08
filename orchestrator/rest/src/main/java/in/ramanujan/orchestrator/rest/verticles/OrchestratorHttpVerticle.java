package in.ramanujan.orchestrator.rest.verticles;

import in.ramanujan.db.layer.constants.Config;
import in.ramanujan.orchestrator.base.configuration.ConfigurationGetter;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import in.ramanujan.orchestrator.rest.handlers.*;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.orchestrator.data.external.OrchestratorApiCaller;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrchestratorHttpVerticle extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(OrchestratorHttpVerticle.class);

    @Autowired
    private OrchestrateHandler orchestrateHandler;

    @Autowired
    private OrchestratorStatusHandler orchestratorStatusHandler;

    @Autowired
    private OpenPingHandler openPingHandler;

    @Autowired
    private HeartbeatPingHandler heartbeatPingHandler;

    @Autowired
    private TaskCompleteHandler taskCompleteHandler;

    @Autowired
    private OrchestratorCheckpointHandler orchestratorCheckpointHandler;

    @Autowired
    private SuspendWorkflowHandler suspendWorkflowHandler;

    @Autowired
    private OrchestratorApiCaller orchestratorApiCaller;

    @Autowired
    private DebugInfoPushHandler debugInfoPushHandler;

    @Autowired
    private OrchestratorCheckpointResumeHandler orchestratorCheckpointResumeHandler;

    @Autowired
    private QueryExecutor queryExecutor;

    @Autowired
    private StorageDao storageDao;


    @Override
    public void start(Future<Void> startFuture) throws Exception {

        startWebApp(new Handler<AsyncResult<HttpServer>>() {
            @Override
            public void handle(AsyncResult<HttpServer> httpServerAsyncResult) {
                completeStartup(httpServerAsyncResult, startFuture);
            }
        });
    }

    private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
//        Router router = createRouter();
//        HttpServerOptions options = new HttpServerOptions();
//        options.setTcpKeepAlive(true);
//        vertx.createHttpServer(options).requestHandler(router::accept).exceptionHandler(
//                event -> {
//                    logger.error("Exception for request: error: {}", event);
//                }
//        ).listen(
//                config().getInteger("event.http.port", 8890), next
//        );
    }

    private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
        if (http.succeeded()) {
            logger.info("Application is up BLABLA");
            fut.complete();
        } else {
            fut.fail(http.cause());
        }
    }

    public void createRouter(Router router) {
        QueryExecutor.DBConfig dbConfig = new QueryExecutor.DBConfig(ConfigurationGetter.getString(Config.DB_URL),
                ConfigurationGetter.getString(Config.DB_USER), ConfigurationGetter.getString(Config.DB_PASSWORD),
                ConfigurationGetter.getString(Config.DB_NAME));
//        queryExecutor.init(context, ConfigurationGetter.getDBType(), dbConfig);
        //storageDao.init(context, ConfigurationGetter.getStorageType());
        healthCheck(router);
        orchestrate(router);
        openPings(router);
        heartBeat(router);
        pingCheckpoint(router);
        resumeCheckpoint(router);
        statusCheck(router);
        taskCompleteHandle(router);
        suspendWorkflow(router);
        debugPushAPI(router);
        clientCreatApis(router);
    }

    private void resumeCheckpoint(Router router) {
        router.post("/checkpoint/resume").handler(orchestratorCheckpointHandler);
    }

    private void debugPushAPI(Router router) {
        router.post("/debugValues").handler(debugInfoPushHandler);
    }

    private void healthCheck(Router router) {
        router.get("/health_check").handler(handler -> {
            JsonObject jsonObject = new JsonObject()
                    .put("health", true);
            handler.response().setStatusCode(200).end(jsonObject.toString());
        });
    }

    private void openPings(Router router) {
        router.post("/pings/open").handler(openPingHandler);
    }

    private void heartBeat(Router router) {
        router.post("/pings/heartbeat").handler(heartbeatPingHandler);
    }

    private void statusCheck(Router router) {
        router.get("/statusorch").handler(orchestratorStatusHandler);
    }

    private void orchestrate(Router router) {
        router.post("/orchestrate").handler(orchestrateHandler);
    }

    private void taskCompleteHandle(Router router) {
        router.post("/task/complete").handler(taskCompleteHandler);
    }

    private void pingCheckpoint(Router router) {
        router.post("/pings/checkpoint").handler(orchestratorCheckpointHandler);
    }

    private void suspendWorkflow(Router router) {
        router.put("/suspend").handler(suspendWorkflowHandler);
    }

    private void clientCreatApis(Router router) {
        router.put("/orchestrator").handler(handler -> {
            MultiMap map = handler.queryParams();
            int port = Integer.parseInt(map.get("port"));
            String ip = map.get("ip");
            orchestratorApiCaller.setWebClient(ip, port);
            handler.response().setStatusCode(200).end();
        });
    }

}
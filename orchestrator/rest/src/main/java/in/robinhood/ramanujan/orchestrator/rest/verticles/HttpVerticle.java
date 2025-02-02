package in.robinhood.ramanujan.orchestrator.rest.verticles;

import in.robinhood.ramanujan.db.layer.utils.QueryExecutor;
import in.robinhood.ramanujan.orchestrator.data.external.OrchestratorApiCaller;
import in.robinhood.ramanujan.orchestrator.data.impl.storageDaoImpl.GoogleCloudStorageImpl;
import in.robinhood.ramanujan.orchestrator.rest.handlers.*;
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
public class HttpVerticle extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(HttpVerticle.class);

    @Autowired
    private OrchestrateHandler orchestrateHandler;

    @Autowired
    private StatusHandler statusHandler;

    @Autowired
    private OpenPingHandler openPingHandler;

    @Autowired
    private HeartbeatPingHandler heartbeatPingHandler;

    @Autowired
    private TaskCompleteHandler taskCompleteHandler;

    @Autowired
    private CheckpointHandler checkpointHandler;

    @Autowired
    private SuspendWorkflowHandler suspendWorkflowHandler;

    @Autowired
    private OrchestratorApiCaller orchestratorApiCaller;

    @Autowired
    private DebugInfoPushHandler debugInfoPushHandler;

    @Autowired
    private CheckpointResumeHandler checkpointResumeHandler;

    @Autowired
    private QueryExecutor queryExecutor;

    @Autowired
    private GoogleCloudStorageImpl googleCloudStorageImpl;


    @Override
    public void start(Future<Void> startFuture) throws Exception {
        queryExecutor.init(context);
        googleCloudStorageImpl.init(context);
        startWebApp(new Handler<AsyncResult<HttpServer>>() {
            @Override
            public void handle(AsyncResult<HttpServer> httpServerAsyncResult) {
                completeStartup(httpServerAsyncResult, startFuture);
            }
        });
    }

    private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
        Router router = createRouter();
        HttpServerOptions options = new HttpServerOptions();
        options.setTcpKeepAlive(true);
        vertx.createHttpServer(options).requestHandler(router::accept).exceptionHandler(
                event -> {
                    logger.error("Exception for request: error: {}", event);
                }
        ).listen(
                config().getInteger("event.http.port", 8890), next
        );
    }

    private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
        if (http.succeeded()) {
            logger.info("Application is up BLABLA");
            fut.complete();
        } else {
            fut.fail(http.cause());
        }
    }

    private Router createRouter() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
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
        return router;
    }

    private void resumeCheckpoint(Router router) {
        router.post("/checkpoint/resume").handler(checkpointHandler);
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
        router.get("/status").handler(statusHandler);
    }

    private void orchestrate(Router router) {
        router.post("/orchestrate").handler(orchestrateHandler);
    }

    private void taskCompleteHandle(Router router) {
        router.post("/task/complete").handler(taskCompleteHandler);
    }

    private void pingCheckpoint(Router router) {
        router.post("/pings/checkpoint").handler(checkpointHandler);
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
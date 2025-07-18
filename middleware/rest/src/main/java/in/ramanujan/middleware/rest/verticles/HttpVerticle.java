package in.ramanujan.middleware.rest.verticles;

import in.ramanujan.data.KafkaManagerApiCaller;
import in.ramanujan.data.OrchestrationApiCaller;
import in.ramanujan.data.db.dao.StorageDao;
import in.ramanujan.data.db.impl.storageDao.StorageDaoGoogleCloudImpl;
import in.ramanujan.db.layer.constants.Config;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import in.ramanujan.middleware.rest.handler.*;
import in.ramanujan.db.layer.utils.ConnectionCreator;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.orchestrator.data.impl.storageDaoImpl.OrchestratorStorageDaoInternal;
import in.ramanujan.orchestrator.rest.verticles.OrchestratorHttpVerticle;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.core.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpVerticle extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(HttpVerticle.class);

    @Autowired
    private TranslateAndRunHandler translateAndRunHandler;

    @Autowired
    private StatusHandler statusHandler;

    @Autowired
    private OrchestratorHttpVerticle orchestratorHttpVerticle;

    @Autowired
    private PackageRunner packageRunner;

    @Autowired
    private WorkflowSuspendHandler workflowSuspendHandler;

    @Autowired
    private ProcessNextDagElementHandler processNextDagElementHandler;

    @Autowired
    private OrchestrationApiCaller orchestrationApiCaller;

    @Autowired
    private KafkaManagerApiCaller kafkaManagerApiCaller;

    @Autowired
    private DebugInformationFetchHandler debugInformationFetchHandler;

    @Autowired
    private CheckpointResumeHandler checkpointResumeHandler;

    @Autowired
    private AddFirstDebugPointHandler addFirstDebugPointHandler;

    @Autowired
    private GetDagElementCodeHandler getDagElementCodeHandler;

    @Autowired
    private ConnectionCreator connectionCreator;

    @Autowired
    private StorageDao storageDao;

    @Autowired
    private OrchestratorStorageDaoInternal orchestratorStorageDaoInternal;

    @Autowired
    private in.ramanujan.orchestrator.data.dao.StorageDao orchestratorStorageDao;

    @Autowired
    private QueryExecutor queryExecutor;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        connectionCreator.init(context);
        QueryExecutor.DBConfig dbConfig = new QueryExecutor.DBConfig(ConfigurationGetter.getString(Config.DB_URL),
                ConfigurationGetter.getString(Config.DB_USER), ConfigurationGetter.getString(Config.DB_PASSWORD),
                ConfigurationGetter.getString(Config.DB_NAME));
        logger.info("DB Config: " +  dbConfig);
        queryExecutor.init(context, ConfigurationGetter.getDBType(), dbConfig);
        logger.info("QueryExecutor initialized with DB Config");
        kafkaManagerApiCaller.vertx = vertx;
        orchestrationApiCaller.vertx = vertx;
        orchestrationApiCaller.context = context;
        orchestrationApiCaller.initialize();
        storageDao.setContext(context, ConfigurationGetter.getStorageType());
        orchestratorStorageDao.init(context, ConfigurationGetter.getStorageType());

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
                config().getInteger("event.http.port", 8888), "0.0.0.0", next
        );
    }

    private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
        if (http.succeeded()) {
            logger.info("Application is up");
            fut.complete();
        } else {
            fut.fail(http.cause());
        }
    }

    private Router createRouter() {
        Router router = Router.router(vertx);
        
        // Add CORS handler for all routes
        CorsHandler corsHandler = CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization")
                .allowedHeader("Accept");
        
        router.route().handler(corsHandler);
        router.route().handler(BodyHandler.create());
        
        runUserCodeHandle(router);
        clientCreatApis(router);
        runHealthCheck(router);
        orchestratorHttpVerticle.createRouter(router);
        return router;
    }

    private void runHealthCheck(Router router) {
        router.get("/health").handler(handler -> {
            handler.response().setStatusCode(200).end("OK");
        });
    }

    private void runUserCodeHandle(Router router) {
        // Handle OPTIONS preflight for /run endpoint
        router.options("/run").handler(handler -> {
            handler.response()
                    .setStatusCode(200)
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .putHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
                    .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
                    .putHeader("Access-Control-Max-Age", "3600")
                    .end();
        });
        
        // Handle OPTIONS preflight for /status endpoint
        router.options("/status").handler(handler -> {
            handler.response()
                    .setStatusCode(200)
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .putHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
                    .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
                    .putHeader("Access-Control-Max-Age", "3600")
                    .end();
        });
        
        // Existing handlers
        router.post("/run").handler(translateAndRunHandler);
        router.post("/run/package").handler(packageRunner);
        router.get("/status").handler(statusHandler);
        router.put("/suspend").handler(workflowSuspendHandler);
        router.put("/process/next").handler(processNextDagElementHandler);
        router.get("/debug").handler(debugInformationFetchHandler);
        router.post("/checkpoint/resume").handler(checkpointResumeHandler);
        router.post("/checkpoints").handler(addFirstDebugPointHandler);
        router.get("/dagElementId").handler(getDagElementCodeHandler);
    }

    private void clientCreatApis(Router router) {
        router.put("/orchestrator").handler(handler -> {
            MultiMap map = handler.queryParams();
            int port = Integer.parseInt(map.get("port"));
            String ip = map.get("ip");
            orchestrationApiCaller.setWebClient(ip, port);
            handler.response().setStatusCode(200).end();
        });

        router.put("/kafka").handler(handler -> {
            MultiMap map = handler.queryParams();
            int port = Integer.parseInt(map.get("port"));
            String ip = map.get("ip");
            kafkaManagerApiCaller.setWebClient(ip, port);
            handler.response().setStatusCode(200).end();
        });
    }

}

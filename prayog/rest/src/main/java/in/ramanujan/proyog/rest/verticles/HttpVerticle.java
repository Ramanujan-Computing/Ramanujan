package in.ramanujan.proyog.rest.verticles;

import in.ramanujan.handler.LoadHandler;
import in.ramanujan.handler.StartSpawnDeviceHandler;
import in.ramanujan.handler.StopSpawnDeviceHandler;
import in.ramanujan.handler.TestHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
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
    private TestHandler testHandler;

    @Autowired
    private StartSpawnDeviceHandler startSpawnDeviceHandler;

    @Autowired
    private StopSpawnDeviceHandler stopSpawnDeviceHandler;

    @Autowired
    private LoadHandler loadHandler;

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
        Router router = createRouter();
        HttpServerOptions options = new HttpServerOptions();
        options.setTcpKeepAlive(true);
        vertx.createHttpServer(options).requestHandler(router::accept).exceptionHandler(
                event -> {
                    logger.error("Exception for request: error: {}", event);
                }
        ).listen(
                config().getInteger("event.http.port", 8887), next
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
        router.route().handler(BodyHandler.create());
        testApiExpose(router);

        spawnDevices(router);

        loadTester(router);

        return router;
    }

    private void spawnDevices(Router router) {
        router.put("/start").handler(startSpawnDeviceHandler);
        router.put("/stop").handler(stopSpawnDeviceHandler);
    }

    private void loadTester(Router router) {
        router.put("/load").handler(loadHandler);
    }

    private void testApiExpose(Router router) {
        router.post("/test").handler(testHandler);
    }
}

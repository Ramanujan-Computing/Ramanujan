package in.ramanujan.rest.verticles;

import in.ramanujan.data.MiddlewareClient;
import in.ramanujan.rest.handler.KafkaProduceRequestHandler;
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
public class KafkaHttpVerticle extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(KafkaHttpVerticle.class);

    @Autowired
    private KafkaProduceRequestHandler kafkaProduceRequestHandler;

    @Autowired
    private MiddlewareClient middlewareClient;

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
                config().getInteger("event.http.port", 8889), "0.0.0.0", next
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
        kafkaRelatedApis(router);
        healthCheck(router);
        clientCreatApis(router);
        return router;
    }

    private void clientCreatApis(Router router) {
        router.put("/middleware").handler(handler -> {
           MultiMap map = handler.queryParams();
           int port = Integer.parseInt(map.get("port"));
           String ip = map.get("ip");
           middlewareClient.setWebClient(ip, port);
           handler.response().setStatusCode(200).end();
        });
    }

    private void kafkaRelatedApis(Router router) {
        router.post("/handle").handler(kafkaProduceRequestHandler);
    }

    private void healthCheck(Router router) {
        router.get("/health_check").handler(handler -> {
            JsonObject jsonObject = new JsonObject()
                    .put("health", true);
            handler.response().setStatusCode(200).end(jsonObject.toString());
        });
    }
}

package in.ramanujan.middleware.rest.verticles;


import in.ramanujan.data.MiddlewareClient;
import in.ramanujan.data.queingDaoImpl.QueueDaoImpl;
import in.ramanujan.middleware.service.ProcessNextDagElementService;
import in.ramanujan.monitoringutils.verticles.MonitoringVerticle;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import in.ramanujan.middleware.base.spring.SpringConfig;
import in.ramanujan.middleware.rest.CustomDeploymentOption;
import in.ramanujan.orchestrator.rest.verticles.OrchestratorHttpVerticle;
import in.ramanujan.rest.verticles.KafkaHttpVerticle;
import in.ramanujan.rest.verticles.kafka.ConsumerVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import in.ramanujan.middleware.rest.verticles.HttpVerticle;

import static in.ramanujan.data.queingDaoImpl.QueueDaoImpl.QUEUE_TYPE;

public class MainVerticle extends AbstractVerticle {
    Logger logger= LoggerFactory.getLogger(MainVerticle.class);
    CustomDeploymentOption option = new CustomDeploymentOption();
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        ConfigurationGetter.init(config());
        in.ramanujan.orchestrator.base.configuration.ConfigurationGetter.init(config());
        in.ramanujan.base.configuration.ConfigurationGetter.init(config());
        logger.info("deploying application verticles");
        vertx.deployVerticle(applicationContext.getBean(HttpVerticle.class), option.getDeployOptions("HttpVerticle", 250));
        //vertx.deployVerticle(applicationContext.getBean(OrchestratorHttpVerticle.class), option.getDeployOptions("OrchHttpVerticle", 250));
        final String projectId = "ramanujan-340512";
        final String metricPusherCredPath = ConfigurationGetter.getString(in.ramanujan.middleware.base.configuration.ConfigKey.METRIC_PUSHER_CRED_PATH);
        final MonitoringVerticle monitoringVerticle = new MonitoringVerticle(projectId, metricPusherCredPath, ConfigurationGetter.getMonitoringType());
        vertx.deployVerticle(monitoringVerticle, option.getDeployOptions("MonitoringVerticle", 1));

        QueueDaoImpl queueDaoImpl = applicationContext.getBean(QueueDaoImpl.class);
        queueDaoImpl.init(QueueDaoImpl.QueueType.fromString(ConfigurationGetter.getString(QUEUE_TYPE)));

        //vertx.deployVerticle(applicationContext.getBean(KafkaHttpVerticle.class), option.getDeployOptions("kafkHttpVerticle", 250));
        MiddlewareClient middlewareClient = applicationContext.getBean(MiddlewareClient.class);
        ProcessNextDagElementService processNextDagElementService = applicationContext.getBean(ProcessNextDagElementService.class);
        middlewareClient.setConsumptionCallback((asyncId, dagElementId, toBeDebugged, vertx) ->
                processNextDagElementService.processNextElement(asyncId, dagElementId, vertx, toBeDebugged));
        vertx.deployVerticle(applicationContext.getBean(ConsumerVerticle.class), option.getDeployOptions("ConsumerVerticle", 250));

//        applicationContext.getBean(ConnectionCreator.class).init(vertx);
    }
}

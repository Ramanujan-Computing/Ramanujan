package in.ramanujan.orchestrator.rest.verticles;

import in.ramanujan.monitoringutils.verticles.MonitoringVerticle;
import in.ramanujan.orchestrator.base.configuration.ConfigurationGetter;
import in.ramanujan.orchestrator.rest.spring.SpringConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class MainVerticle extends AbstractVerticle {
    Logger logger= LoggerFactory.getLogger(MainVerticle.class);
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        CustomDeploymentOption customDeploymentOption = new CustomDeploymentOption();
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        logger.info("deploying application verticles");
        ConfigurationGetter.init(config());
        vertx.deployVerticle(applicationContext.getBean(HttpVerticle.class), customDeploymentOption.getDeployOptions(
                HttpVerticle.class.getName(), 100
        ));
        final String projectId = "ramanujan-340512";
        final String metricPusherCredPath = ConfigurationGetter.getString(in.ramanujan.orchestrator.base.configuration.ConfigKey.METRIC_PUSHER_CRED_PATH);
        final MonitoringVerticle monitoringVerticle = new MonitoringVerticle(projectId, metricPusherCredPath, ConfigurationGetter.getMonitoringType());
        vertx.deployVerticle(monitoringVerticle, customDeploymentOption.getDeployOptions("MonitoringVerticle", 1));
//        vertx.deployVerticle(applicationContext.getBean(PingerVerticle.class),customDeploymentOption
//                .getDeployOptions(PingerVerticle.class.getName(), config().getInteger("pinger.worker.size")));
//        applicationContext.getBean(ConnectionCreator.class).init(vertx);
    }
}
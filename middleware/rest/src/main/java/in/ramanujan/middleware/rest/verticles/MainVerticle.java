package in.ramanujan.middleware.rest.verticles;


import in.ramanujan.monitoringutils.verticles.MonitoringVerticle;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import in.ramanujan.middleware.base.spring.SpringConfig;
import in.ramanujan.middleware.rest.CustomDeploymentOption;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import in.ramanujan.middleware.rest.verticles.HttpVerticle;

public class MainVerticle extends AbstractVerticle {
    Logger logger= LoggerFactory.getLogger(MainVerticle.class);
    CustomDeploymentOption option = new CustomDeploymentOption();
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        ConfigurationGetter.init(config());
        logger.info("deploying application verticles");
        vertx.deployVerticle(applicationContext.getBean(HttpVerticle.class), option.getDeployOptions("HttpVerticle", 250));
        final String projectId = "ramanujan-340512";
        final String metricPusherCredPath = ConfigurationGetter.getString(in.ramanujan.middleware.base.configuration.ConfigKey.METRIC_PUSHER_CRED_PATH);
        final MonitoringVerticle monitoringVerticle = new MonitoringVerticle(projectId, metricPusherCredPath, ConfigurationGetter.getMonitoringType());
        vertx.deployVerticle(monitoringVerticle, option.getDeployOptions("MonitoringVerticle", 1));
//        applicationContext.getBean(ConnectionCreator.class).init(vertx);
    }
}

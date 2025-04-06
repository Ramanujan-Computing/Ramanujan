package in.ramanujan.proyog.rest.verticles;

import in.ramanujan.prayog.rest.spring.SpringConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static in.ramanujan.data.PrayogConfig.CONFIG;

public class MainVerticle extends AbstractVerticle {
    Logger logger= LoggerFactory.getLogger(MainVerticle.class);
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        CONFIG(config());
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        CustomDeploymentOption customDeploymentOption = new CustomDeploymentOption();
        logger.info("deploying application verticles");
        vertx.deployVerticle(applicationContext.getBean(HttpVerticle.class),
                customDeploymentOption.getDeployOptions(this.getClass().getName(), 100));
    }
}

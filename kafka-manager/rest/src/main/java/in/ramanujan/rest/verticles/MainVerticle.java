package in.ramanujan.rest.verticles;

import in.ramanujan.base.configuration.ConfigurationGetter;
import in.ramanujan.rest.CustomDeploymentOption;
import in.ramanujan.rest.verticles.kafka.ConsumerVerticle;
import in.ramanujan.rest.spring.SpringConfig;
import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


/*
* Entry point of the service. Deploys 3 verticles:
* 1> HttpVerticle: Verticle which will start the server for listening to API calls.
* 2> SampleConsumer: Verticle which will consume kafka events
*/
public class MainVerticle extends AbstractVerticle {
    Logger logger= LoggerFactory.getLogger(MainVerticle.class);
    CustomDeploymentOption customDeploymentOption = new CustomDeploymentOption();
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        ConfigurationGetter.init(config());

        logger.info("deploying application verticles");
        deploy(applicationContext.getBean(ConsumerVerticle.class), ConsumerVerticle.class.getName());
        deploy(applicationContext.getBean(KafkaHttpVerticle.class), KafkaHttpVerticle.class.getName());
    }

    private void deploy(Verticle verticle, String className) {
        vertx.deployVerticle(verticle,
                customDeploymentOption.getDeployOptions(className, 100), handler -> {
                    if(handler.succeeded()) {
                        logger.info("verticle deployed: " + verticle.getClass().getName());
                    } else {
                        logger.error("error", handler.cause());
                        System.exit(1);
                    }
                });
    }
}

package in.ramanujan.data.logging;

import io.vertx.core.logging.LoggerFactory;

public class Logger implements in.ramanujan.devices.common.logging.Logger {

    private io.vertx.core.logging.Logger logger;

    public Logger(Class clazz) {
         logger = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void info(Object o) {
        logger.info(o);
    }

    @Override
    public void error(Object o, Throwable throwable) {
        logger.error(o, throwable);
    }
}

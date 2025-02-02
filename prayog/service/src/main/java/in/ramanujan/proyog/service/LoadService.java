package in.ramanujan.proyog.service;

import in.ramanujan.data.MiddlewareApiCaller;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class LoadService {

    private Logger logger = LoggerFactory.getLogger(LoadService.class);

    @Autowired
    private MiddlewareApiCaller middlewareApiCaller;

    Long failure = 0l;
    Long total = 0l;

    public Future<Void> startTest(int users, JsonObject execCode) {
        for(int i=0;i<users; i++) {
            spawnUser(execCode);
        }
        return Future.succeededFuture();
    }

    private void spawnUser(JsonObject execCode) {
        total ++;
        middlewareApiCaller.run(execCode, new Date().toInstant().toEpochMilli()).setHandler(handler -> {
            if(handler.failed()) {
                failure ++;
            }
            logger.info("failure % " + failure + " / " + total);
            spawnUser(execCode);
        });
    }

}

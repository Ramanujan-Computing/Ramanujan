package in.ramanujan.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.service.EventProducer;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KafkaProduceRequestHandler implements Handler<RoutingContext> {

    private ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger= LoggerFactory.getLogger(KafkaProduceRequestHandler.class);

    @Autowired
    private EventProducer eventProducer;

    @Override
    public void handle(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        CheckStatusQueueEvent checkStatusQueueEvent = body.mapTo(CheckStatusQueueEvent.class);
        logger.info(body.toString());
        logger.info("to be debuged?" + checkStatusQueueEvent.getToBeDebugged());
        eventProducer.produce(checkStatusQueueEvent).setHandler(handler -> {
           if(handler.succeeded()) {
               routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
           } else {
               logger.error(handler.cause());
               routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
           }
        });
    }
}

package in.ramanujan.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.proyog.service.TriggerRuleEngine;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class TestHandler implements Handler<RoutingContext> {

    private Logger logger = LoggerFactory.getLogger(TestHandler.class);

    private ObjectMapper objectMapper;

    @Autowired
    private TriggerRuleEngine triggerRuleEngine;

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            JsonObject payload = routingContext.getBodyAsJson();
            RuleEngineInput ruleEngineInput = payload.getJsonObject("ruleEngineInput").mapTo(RuleEngineInput.class);
            String firstCommandId = payload.getString("firstCommandId");
            triggerRuleEngine.triggerRuleEngine(ruleEngineInput, firstCommandId, Vertx.vertx()).setHandler(handler -> {
                if(handler.succeeded()) {
                    logger.info("test passed: map : " + handler.result());
                    routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(handler.result() + "");
                } else {
                    logger.error("test failed: ", handler.cause());
                    routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
                }
            });
        } catch (Exception e) {
            logger.error("the test failed due to ", e);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }
}

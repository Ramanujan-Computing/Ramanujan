package in.ramanujan.orchestrator.rest.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.orchestrator.base.pojo.ApiResponse;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.rest.verticles.OrchestratorHttpVerticle;
import in.ramanujan.orchestrator.service.OrchestrateService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrchestrateHandler implements Handler<RoutingContext> {

    private Logger logger = LoggerFactory.getLogger(OrchestratorHttpVerticle.class);

    @Autowired
    private OrchestrateService orchestrateService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(RoutingContext event) {
        try {
            //TODO: remove the use of ruleEngineInput.getVariable(), getArray();
            JsonObject input = event.getBodyAsJson();
//            RuleEngineInput ruleEngineInput = objectMapper.readValue(input.getJsonObject("ruleEngineInput").toString(),
//                    RuleEngineInput.class);
            String firstCommandId = input.getString("firstCommandId");
            String dagElementId = input.getString("dagElementId");
            String orchestratorAsyncId = input.getString("orchestratorAsyncId");
            Boolean debuggable = input.getBoolean("debug");
            String commaSeparatedDebugLines = input.getString("debugLines");
            List<Integer> debugLines = new ArrayList<>();
            if (commaSeparatedDebugLines != null) {
                String[] debugLinesStr = commaSeparatedDebugLines.split(",");
                for (String debugLineStr : debugLinesStr) {
                    debugLines.add(Integer.parseInt(debugLineStr.trim()));
                }
            }
            if(debuggable == null) {
                debuggable = false;
            }
            orchestrateService.orchestrateService(firstCommandId, orchestratorAsyncId, debuggable, debugLines)
                    .setHandler(new MonitoringHandler<>("orchestrateService", handler-> {
                try {
                    if (handler.succeeded()) {
                        logger.info("Async taks added");
                        ApiResponse apiResponse = new ApiResponse(Status.SUCCESS.getKeyName(), orchestratorAsyncId);
                        event.response().setStatusCode(HttpResponseStatus.OK.code()).end(objectMapper.writeValueAsString(apiResponse));
                    } else {
                        logger.error("Async task not added", handler.cause());
                        ApiResponse apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), handler.cause());
                        event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(JsonObject.mapFrom(apiResponse).toString());
                    }
                } catch (Exception e) {
//                    event.response().setStatusCode()
                }
            }));

        } catch (Exception e) {
            logger.error("Exception raised ", e);
            ApiResponse apiResponse = new ApiResponse(Status.FAILURE.getKeyName(), e);
            event.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                    .end(JsonObject.mapFrom(apiResponse).toString());
        }

    }
}

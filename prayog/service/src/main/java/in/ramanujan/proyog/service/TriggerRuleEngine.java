package in.ramanujan.proyog.service;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.rule.engine.Processor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TriggerRuleEngine {
    public Future<Object> triggerRuleEngine(RuleEngineInput ruleEngineInput, String firstCommandId, Vertx vertx) {
//        Future<Object> future = Future.future();
//        vertx.executeBlocking(ruleEngineExec -> {
//            Processor processor = new Processor(ruleEngineInput, firstCommandId);
//            try {
//                Map<String, Object> resultOfProcessing = processor.process();
//                ruleEngineExec.complete(resultOfProcessing);
//            } catch (Exception e) {
//                ruleEngineExec.fail(e);
//            }
//        }, false, res -> {
//            if(res.succeeded()) {
//                future.complete(res.result());
//            } else {
//                future.fail(res.cause());
//            }
//        });
        return Future.succeededFuture();
    }
}

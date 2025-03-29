package in.ramanujan.data;

import in.ramanujan.devices.common.Config;
import in.ramanujan.devices.common.Credentials.Credentials;
import in.ramanujan.devices.common.RamanujanController;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.rule.engine.Processor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceMock {

    private Logger logger = LoggerFactory.getLogger(DeviceMock.class);

    Boolean devicesRunning = false;

    Map<String, Boolean> processingLogger = new ConcurrentHashMap<>();
    Set<Long> pingTimerSet = new HashSet<>();
    Set<Long> processInputFetchTimerSet = new HashSet<>();

    private Map<String, Processor> hostProcessorMap = new HashMap<>();

    @Autowired
    private OrchestratorApiCaller orchestratorApiCaller;

    public Future<Void> mock(Vertx vertx) {
        devicesRunning = true;

            try {
                //runMachine(vertx);
                JsonObject config = vertx.getOrCreateContext().config();
                logger.info(Thread.currentThread().getName());
                RamanujanController ramanujanController = new RamanujanController(config.getString(Config.ORCHESTRATOR_URL),
                        new in.ramanujan.data.logging.LoggerFactory());
                ramanujanController.startOrchestrations();
            } catch (Exception e) {

            }


        return Future.succeededFuture();
    }

//    private void runMachine(Vertx vertx) {
//        String uuid = UUID.randomUUID().toString();
//
//        pingTimerSet.add(vertx.setPeriodic(1000, handler -> {
//            //logger.info("pinging heartbeat for " + uuid);
//            orchestratorApiCaller.sendHeartBeat(uuid, hostProcessorMap);
//        }));
//        processInputFetchTimerSet.add(vertx.setPeriodic(5, hander -> {
//            if (processingLogger.get(uuid) == null || !processingLogger.get(uuid)) {
//                processingLogger.put(uuid, true);
//                orchestratorApiCaller.getProcessingInput(uuid).setHandler(processingInputFetcher -> {
//                    if (processingInputFetcher.failed() || processingInputFetcher.result() == null) {
//                        processingLogger.put(uuid, false);
//                    } else {
//                        for (String firstCommandId : processingInputFetcher.result().keySet()) {
//                            execute(vertx, processingInputFetcher.result().get(firstCommandId), firstCommandId, uuid)
//                                    .setHandler(executor -> {
//                                        //logger.info("execution done for " + uuid + "\n" + executor.result() + "\n\n");
//                                        orchestratorApiCaller.submitProcessingInputWithRetries(uuid, executor.result())
//                                                .setHandler(resultSubmitter -> {
//                                                    processingLogger.put(uuid, false);
//                                                });
//                            });
//                        }
//                    }
//                });
//            }
//        }));
//    }
//
//    private Future<Map<String, Object>> execute(Vertx vertx, RuleEngineInput ruleEngineInput, String firstCommandId,
//                                                String hostId) {
//        Future<Map<String, Object>> future = Future.future();
//        Processor processor = new Processor(ruleEngineInput, firstCommandId);
//        hostProcessorMap.put(hostId, processor);
//        vertx.executeBlocking(processing -> {
//            try {
//
//                Long start = new Date().toInstant().toEpochMilli();
//                Map<String, Object> result = processor.process();
//                processor.endProcess();
//                logger.info("Took time: " + (new Date().toInstant().toEpochMilli() - start)/1000);
//                hostProcessorMap.remove(hostId);
//                processing.complete(result);
//            } catch (Exception e) {
//                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                processing.fail(e);
//            }
//        }, false, res -> {
//            future.complete((Map) (res.result()));
//        });
//        return future;
//    }

    public void stopMock(Vertx vertx) {
        processingLogger = new HashMap<>();
        /*
         * Flush heartbeat timer, and ruleEngineInputFetcher timer
         * */
        for (Long timerId : pingTimerSet) {
            vertx.cancelTimer(timerId);
        }
        for (Long timerId : processInputFetchTimerSet) {
            vertx.cancelTimer(timerId);
        }
        pingTimerSet = new HashSet<>();
        processInputFetchTimerSet = new HashSet<>();
        devicesRunning = false;
    }
}

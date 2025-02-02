package in.ramanujan.devices.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.debugger.UserReadableDebugPoint;
import in.ramanujan.devices.common.logging.Logger;
import in.ramanujan.devices.common.logging.LoggerFactory;
import in.ramanujan.devices.common.pojo.OpenPingApiResponse;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.CheckpointProcessor;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.debugger.IDebugPushClient;
import lombok.Data;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static in.ramanujan.utils.Constants.arrayIndex;

public class CodeExecutor {

    static Logger logger;

    final static ObjectMapper objectMapper = new ObjectMapper();

    static void init(final LoggerFactory loggerFactory) {
        logger = loggerFactory.getLogger(CodeExecutor.class);
    }

    static ProcessorFutureMap execute(final OpenPingApiResponse openPingApiResponse, final String hostId, IDebugPushClient debugPushClient) {
        final RuleEngineInput ruleEngineInput = openPingApiResponse.getRuleEngineInput();
//        Checkpoint checkpoint = openPingApiResponse.getCheckpoint();
//        String firstCommandId = openPingApiResponse.getFirstCommandId();
//
//        Processor processor;
        final CheckpointPushClient checkpointPushClient = new CheckpointPushClient(openPingApiResponse.getUuid());
//        if(checkpoint != null) {
//            logger.info("Resuming checkpoint on " + hostId);
//            processor = new CheckpointProcessor(ruleEngineInput, checkpoint, checkpointPushClient);
//        } else {
//            processor = new Processor(ruleEngineInput, firstCommandId, checkpointPushClient);
//        }
//        final Set<Integer> debugPointSet;
//        if(openPingApiResponse.getBreakpoints() != null) {
//            debugPointSet = new HashSet<>(openPingApiResponse.getBreakpoints());
//        } else {
//            debugPointSet = new HashSet<>();
//        }
//        processor.setToBeDebugged(openPingApiResponse.getDebug(), debugPushClient, debugPointSet);
        ProcessorFutureMap processorFutureMap = new ProcessorFutureMap();
//        processorFutureMap.setProcessor(processor);

        AtomicInteger integer = new AtomicInteger(0);
        String fileName = "/Users/pranav/Desktop/debug_";
        new Thread(() -> {
            try {
                logger.info("start code exec for " + hostId);
                long start = new Date().toInstant().toEpochMilli();
                NativeProcessor nativeProcessor = new NativeProcessor();
                Map<String, Object> results = new HashMap<>();
                if(openPingApiResponse.getFirstCommandId() != "") {
                    nativeProcessor.process(objectMapper.writeValueAsString(ruleEngineInput), openPingApiResponse.getFirstCommandId());
                    results = nativeProcessor.jniObject;

//                    Processor processor = new Processor(ruleEngineInput, openPingApiResponse.getFirstCommandId(), checkpointPushClient);
//                    Map<String, Object> results2 = processor.process();
//                    processor.endProcess();
//
//                    //iterate results2
//                    for (Map.Entry<String, Object> entry : results2.entrySet()) {
//                        if(entry.getKey().equalsIgnoreCase(arrayIndex)) {
//                            Map<String , Map<String, Double>> result2Map = (Map<String, Map<String, Double>>) entry.getValue();
//                            Map<String , Map<String, Double>> result1Map = (Map<String, Map<String, Double>>) results.get(entry.getKey());
//                            for (Map.Entry<String, Map<String, Double>> entry1 : result2Map.entrySet()) {
//                                if(result1Map == null || !result1Map.containsKey(entry1.getKey())) {
//                                    throw new RuntimeException("entry is wrong");
//                                }
//                                Map<String, Double> result1MapInner = result1Map.get(entry1.getKey());
//                                Map<String, Double> result2MapInner = entry1.getValue();
//                                for (Map.Entry<String, Double> entry2 : result2MapInner.entrySet()) {
//                                    if(result1MapInner == null || !result1MapInner.containsKey(entry2.getKey())) {
//                                        throw new RuntimeException("entry is wrong");
//                                    }
//                                    if(!isTolerable((Double) result1MapInner.get(entry2.getKey()).doubleValue(), ((Double)entry2.getValue()).doubleValue())) {
//                                        throw new RuntimeException("entry is wrong");
//                                    }
//                                }
//                            }
//                        } else {
//                            if(!isTolerable(((Double)entry.getValue()).doubleValue(), ((Double)results.get(entry.getKey())).doubleValue())) {
//                                throw new RuntimeException("entry is wrong");
//                            }
//                        }
//                    }
                }
                if(results == null) {
                    results = new HashMap<>();
                }
//                Map<String, Object> results = new Processor(ruleEngineInput, openPingApiResponse.getFirstCommandId(), new CheckpointPushClient(openPingApiResponse.getUuid())).process();
                processorFutureMap.setResult(results);
                logger.info("end code exec for " + hostId);
                logger.info("total time " + (new Date().toInstant().toEpochMilli() - start));
                processorFutureMap.setDone(true);
            } catch (Throwable e) {
                logger.error("ERROR for " + hostId, e);
            }
        }).start();
        return processorFutureMap;
    }

    @Data
    static class ProcessorFutureMap {
        private Processor processor;
        private Map<String, Object> result;
        private List<UserReadableDebugPoint> debugData;
        private Boolean done = false;
    }

    private static  boolean isTolerable(double f1, double f2) {
        if (f1 > 0)
        return Math.abs(f1 - f2) / Math.abs(f1) < 0.01;
        return Math.abs(f1 - f2) < 0.01;
    }

}

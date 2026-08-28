package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import in.ramanujan.translation.codeConverter.utils.TranslateUtil;
import in.ramanujan.rule.engine.RuleEngineInputProtoSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import static in.ramanujan.developer.console.operationImpl.ExecutorImpl.createJson;

/**
 * This class contains the core logic from DebugFetcher, excluding debug file and server creation.
 * Execution mode is controlled by the environment variable RAMANUJAN_SEQUENTIAL:
 *   - unset / "false"  →  parallel execution (default, best throughput)
 *   - "true"           →  sequential execution (deterministic, easier to debug)
 */
public class ExecuteInline implements Operation {
    protected final TranslateUtil translateUtil = new TranslateUtil();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    private boolean shouldWriteRuleEngineDebug() {
        return "true".equalsIgnoreCase(System.getenv("RAMANUJAN_WRITE_RULE_ENGINE_DEBUG"));
    }

    private int resolveThreadPoolSize() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        String configuredParallelism = System.getenv("RAMANUJAN_MAX_PARALLELISM");
        if (configuredParallelism == null || configuredParallelism.trim().isEmpty()) {
            return availableProcessors;
        }
        try {
            int parsedParallelism = Integer.parseInt(configuredParallelism.trim());
            if (parsedParallelism < 1) {
                return 1;
            }
            if (parsedParallelism > availableProcessors) {
                return availableProcessors;
            }
            return parsedParallelism;
        } catch (NumberFormatException ignored) {
            return availableProcessors;
        }
    }


    // Hook for subclasses: called before processing a DAG element
    protected void preProcess(DagElement dagElement) throws IOException {
        // Default: do nothing
    }

    // Hook for subclasses: called after processing a DAG element
    protected void postProcess(DagElement dagElement, in.ramanujan.rule.engine.NativeProcessor nativeProcessor) throws IOException {
        // Default: do nothing
    }

    protected void executeDagElement(DagElement dagElement, Map<String, Variable> variableMap, Map<String, Array> arrayMap) throws IOException {
        preProcess(dagElement);
        if(dagElement.getFirstCommandId().isEmpty()) {
            System.out.println("[DAG] element " + dagElement.getId() + " completed (empty — no commands)");
            postProcess(dagElement, null);
            return;
        }
        RuleEngineInput ruleEngineInput = dagElement.getRuleEngineInput();
        byte[] ruleEngineInputProto = RuleEngineInputProtoSerializer.serialize(ruleEngineInput);
        if (shouldWriteRuleEngineDebug()) {
            // Write protobuf byte array payload that Test.cpp loadFromTmp() expects
            Files.write(new File("/tmp/rule_engine_debug.pb").toPath(), ruleEngineInputProto);
            Files.write(new File("/tmp/rule_engine_debug.json").toPath(), ruleEngineInputProto);
            objectMapper.writeValue(new File("/tmp/rule_engine_debug_meta.json"),
                    Collections.singletonMap("firstCommandId", dagElement.getFirstCommandId()));
        }
        System.out.println("[ExecuteInline] Calling NativeProcessor for DAG element " + dagElement.getId() + " (firstCmd=" + dagElement.getFirstCommandId() + ")");
        System.out.println("[ExecuteInline]   RuleEngineInput protobuf size: " + (ruleEngineInputProto.length / 1024) + " KB");
        System.out.flush();
        long nativeStart = System.currentTimeMillis();
        in.ramanujan.rule.engine.NativeProcessor nativeProcessor = new in.ramanujan.rule.engine.NativeProcessor();
        nativeProcessor.process(ruleEngineInputProto, dagElement.getFirstCommandId());
        System.out.println("[ExecuteInline]   NativeProcessor completed in " + (System.currentTimeMillis() - nativeStart) + "ms");
        System.out.flush();
        for(Object en : nativeProcessor.jniObject.entrySet()) {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) en;
            String key = entry.getKey();
            Object value = entry.getValue();
            if("arrayIndex".equalsIgnoreCase(key)) {
                Map<String, Map<String, Object>> arrayResultMap = (Map<String, Map<String, Object>>) value;
                for(Map.Entry<String, Map<String, Object>> arrayResultEntry : arrayResultMap.entrySet()) {
                    String arrayName = arrayResultEntry.getKey();
                    Map<String, Object> arrayResult = arrayResultEntry.getValue();
                    Array array = arrayMap.get(arrayName);
                    if(array == null) {
                        throw new IOException("Array not found");
                    }
                    array.getValues().putAll(arrayResult);
                }
            } else {
                Variable variable = variableMap.get(key);
                if(variable == null) {
                    throw new IOException("Variable not found");
                }
                variable.setValue(value);
            }
        }
        System.out.println("[DAG] element " + dagElement.getId() + " completed (firstCmd=" + dagElement.getFirstCommandId() + ")");
        postProcess(dagElement, nativeProcessor);
    }

    /**
     * Execute DAG elements sequentially in topological order (debug mode).
     * Before each native call, the element's RuleEngineInput is written to
     * /tmp/rule_engine_debug.json so the standalone native Test binary can
     * reproduce the exact crashing input.
     */
    protected void executeSequentially(DagElement firstDagElement, List<DagElement> dagElementList,
                                       Map<String, Variable> variableMap, Map<String, Array> arrayMap) throws IOException {
        Set<DagElement> allElements = new LinkedHashSet<>(dagElementList);
        allElements.add(firstDagElement);
        Set<DagElement> completed = new HashSet<>();
        Deque<DagElement> readyQueue = new ArrayDeque<>();
        readyQueue.add(firstDagElement);

        while (completed.size() < allElements.size()) {
            if (readyQueue.isEmpty()) {
                // Find any element whose predecessors are all done
                for (DagElement el : allElements) {
                    if (!completed.contains(el) && completed.containsAll(el.getPreviousElements())) {
                        readyQueue.add(el);
                    }
                }
            }
            if (readyQueue.isEmpty()) {
                break; // no progress possible (cycle or done)
            }

            DagElement element = readyQueue.poll();
            if (completed.contains(element)) {
                continue;
            }

            executeDagElement(element, variableMap, arrayMap);
            completed.add(element);

            for (DagElement next : element.getNextElements()) {
                if (!completed.contains(next) && completed.containsAll(next.getPreviousElements())) {
                    readyQueue.add(next);
                }
            }
        }
    }

    /**
     * Execute DAG elements in parallel where possible, respecting dependencies
     */
    protected void executeInParallel(DagElement firstDagElement, List<DagElement> dagElementList,
                                   Map<String, Variable> variableMap, Map<String, Array> arrayMap) throws IOException {
        // Use synchronized collections for thread safety
        Set<DagElement> completedElements = Collections.synchronizedSet(new HashSet<>());
        Set<DagElement> allElements = new HashSet<>(dagElementList);
        allElements.add(firstDagElement);
        
        // Create thread pool - configurable for large DAGs that would otherwise exhaust heap
        int threadPoolSize = resolveThreadPoolSize();
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        
        try {
            // Start with the first element
            Set<DagElement> readyQueue = Collections.synchronizedSet(new HashSet<>());
            readyQueue.add(firstDagElement);

            while (completedElements.size() < allElements.size()) {
                // Find all elements that are ready to execute (dependencies satisfied)
                List<DagElement> readyToExecute = new ArrayList<>();
                
                synchronized (readyQueue) {
                    Iterator<DagElement> iterator = readyQueue.iterator();
                    while (iterator.hasNext()) {
                        DagElement element = iterator.next();
                        if (completedElements.contains(element)) {
                            iterator.remove();
                            continue;
                        }
                        
                        boolean dependenciesSatisfied = true;
                        if (!element.getPreviousElements().isEmpty()) {
                            for (DagElement dependency : element.getPreviousElements()) {
                                if (!completedElements.contains(dependency)) {
                                    dependenciesSatisfied = false;
                                    break;
                                }
                            }
                        }
                        
                        if (dependenciesSatisfied) {
                            readyToExecute.add(element);
                            iterator.remove();
                        }
                    }
                }
                
                if (readyToExecute.isEmpty()) {
                    // Check if any new elements have become ready
                    for (DagElement element : allElements) {
                        if (!completedElements.contains(element)) {
                            boolean dependenciesSatisfied = true;
                            if (!element.getPreviousElements().isEmpty()) {
                                for (DagElement dependency : element.getPreviousElements()) {
                                    if (!completedElements.contains(dependency)) {
                                        dependenciesSatisfied = false;
                                        break;
                                    }
                                }
                            }
                            if (dependenciesSatisfied) {
                                synchronized (readyQueue) {
                                    readyQueue.add(element);
                                }
                            }
                        }
                    }
                    
                    // If still nothing ready, wait a bit
                    if (readyToExecute.isEmpty()) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Execution interrupted", e);
                        }
                        continue;
                    }
                }
                
                // Submit ready elements for parallel execution
                List<Future<Void>> futures = new ArrayList<>();
                for (DagElement element : readyToExecute) {
                    Future<Void> future = executorService.submit(() -> {
                        try {
                            executeDagElement(element, variableMap, arrayMap);
                            completedElements.add(element);
                            
                            // Add next elements to ready queue
                            synchronized (readyQueue) {
                                readyQueue.addAll(element.getNextElements());
                            }
                            return null;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    futures.add(future);
                }
                
                // Wait for all submitted tasks to complete
                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Execution interrupted", e);
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException && cause.getCause() instanceof IOException) {
                            throw (IOException) cause.getCause();
                        } else {
                            throw new IOException("Execution failed", cause);
                        }
                    }
                }
            }
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void execute(List<String> args) throws IOException {
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Variable> variableMap = new HashMap<>();
            Map<String, Array> arrayMap = new HashMap<>();
            System.out.println("[ExecuteInline] Creating JSON from args: " + args.get(0) + " + " + (args.size()-1) + " CSV files");
            System.out.flush();
            CodeRunRequest codeRunRequest = createJson(args);
            String code = codeRunRequest.getCode();
            List<CsvInformation> csvInformationList = codeRunRequest.getCsvInformationList() != null
                    ? codeRunRequest.getCsvInformationList() : new ArrayList<>();
            System.out.println("[ExecuteInline] CSV files loaded: " + csvInformationList.size());
            for (CsvInformation ci : csvInformationList) {
                String d = ci.getData();
                System.out.println("[ExecuteInline]   " + ci.getFileName() + " -> " + (d == null ? "null" : (d.length() / 1024) + " KB"));
            }
            System.out.flush();
            TranslateResponse translateResponse = new TranslateResponse();
            Map<String, RuleEngineInput> functionCallsRuleEngineInput = new HashMap<>();
            ActualDebugCodeCreator actualDebugCodeCreator = new ActualDebugCodeCreator("", 0);
            
            boolean isPython = TranslateUtil.isPythonCode(code);
            String extractedCode;
            String functionCode = "";
            int linesForFunctions;
            
            if (isPython) {
                // For Python code, skip function extraction
                extractedCode = code;
                linesForFunctions = 0;
            } else {
                // For Ramanujan code, extract functions as before
                ExtractedCodeAndFunctionCode extractedCodeAndFunctionCode =
                        translateUtil.extractCodeWithoutAbstractCodeDeclaration(code, functionCallsRuleEngineInput, actualDebugCodeCreator);
                for(Map.Entry<String, RuleEngineInput> entry : functionCallsRuleEngineInput.entrySet()) {
                    for(Variable variable : entry.getValue().getVariables()) {
                        variableMap.put(variable.getId(), variable);
                    }
                    for(Array array : entry.getValue().getArrays()) {
                        arrayMap.put(array.getId(), array);
                    }
                }
                extractedCode = extractedCodeAndFunctionCode.getExtractedCode();
                functionCode = extractedCodeAndFunctionCode.getFunctionCode();
                linesForFunctions = actualDebugCodeCreator.getLine();
            }
            
            CodeSnippetElement firstCodeSnippetElement = translateUtil.getCodeSnippets(extractedCode, new HashMap<>(),
                    new HashMap<>(), new HashMap<>());
            List<DagElement> dagElementList = new ArrayList<>();
            Map<String, String> dagElementAndCodeMap = new HashMap<>();
            DagElement firstDagElement = translateUtil.populateAllDagElements(firstCodeSnippetElement, csvInformationList,
                    functionCallsRuleEngineInput, variableMap, arrayMap, dagElementList, dagElementAndCodeMap, linesForFunctions);
            translateResponse.setFirstDagElement(firstDagElement);
            translateResponse.setDagElementList(dagElementList);
            translateResponse.setCodeAndDagElementMap(dagElementAndCodeMap);
            translateResponse.setCommonFunctionCode(functionCode);

            System.out.println("[ExecuteInline] compilation time: " + (System.currentTimeMillis() - startTime) + "ms");
            System.out.println("[ExecuteInline] DAG elements: " + (dagElementList.size() + 1) + " (1 first + " + dagElementList.size() + " others)");
            System.out.println("[ExecuteInline] Variables: " + variableMap.size() + ", Arrays: " + arrayMap.size());
            System.out.flush();

            startTime = System.currentTimeMillis();
            
            // Execution mode: parallel (default) or sequential (set RAMANUJAN_SEQUENTIAL=true)
            boolean runSequentially = "true".equalsIgnoreCase(System.getenv("RAMANUJAN_SEQUENTIAL"));
            if (runSequentially) {
                System.out.println("Executing DAG sequentially (RAMANUJAN_SEQUENTIAL=true)");
                executeSequentially(firstDagElement, dagElementList, variableMap, arrayMap);
            } else {
                System.out.println("Executing DAG in parallel");
                executeInParallel(firstDagElement, dagElementList, variableMap, arrayMap);
            }

            System.out.println("execution time: " + (System.currentTimeMillis() - startTime) + "ms");

            // Convert variableMap to <String, Object> for the console
            Map<String, Object> variableStoreMap = new HashMap<>();
            for (Variable v : variableMap.values()) {
                variableStoreMap.put(v.getName(), v.getValue());
            }
            // Use custom logic for arrayStoreMap population
            Map<String, Map<String, Object>> arrayStoreMap = new HashMap<>();
            for (Array a : arrayMap.values()) {
                String id = a.getId();
                if (id.contains("func")) {
                    continue;
                }
                if (!id.contains("_name_")) {
                    continue;
                }
                String name = id.split("_name_")[1];
                Map<String, Object> values = a.getValues();
                if (values != null) {
                    for (Map.Entry<String, Object> entry : values.entrySet()) {
                        String indexStr = entry.getKey();
                        Object value = entry.getValue();
                        Map<String, Object> arrMap = arrayStoreMap.getOrDefault(name, new java.util.HashMap<>());
                        arrMap.put(indexStr, value);
                        arrayStoreMap.put(name, arrMap);
                    }
                }
            }
            in.ramanujan.developer.console.operationImpl.ExecutorImpl.setStores(variableStoreMap, arrayStoreMap);
            in.ramanujan.developer.console.operationImpl.ExecutorImpl.startQueryConsole();
        } catch (CompilationException e) {
            throw new IOException(e);
        }
    }
}

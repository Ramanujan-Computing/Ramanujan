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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static in.ramanujan.developer.console.operationImpl.ExecutorImpl.createJson;

/**
 * This class contains the core logic from DebugFetcher, excluding debug file and server creation.
 * It executes DAG elements in parallel where possible, respecting dependencies for optimal performance.
 */
public class ExecuteInline implements Operation {
    protected final TranslateUtil translateUtil = new TranslateUtil();
    protected final ObjectMapper objectMapper = new ObjectMapper();


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
            postProcess(dagElement, null);
            return;
        }
        in.ramanujan.rule.engine.NativeProcessor nativeProcessor = new in.ramanujan.rule.engine.NativeProcessor();
        nativeProcessor.process(objectMapper.writeValueAsString(dagElement.getRuleEngineInput()), dagElement.getFirstCommandId());
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
        postProcess(dagElement, nativeProcessor);
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
        
        // Create thread pool - using number of available processors
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
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
            CodeRunRequest codeRunRequest = createJson(args);
            String code = codeRunRequest.getCode();
            List<CsvInformation> csvInformationList = new ArrayList<>();
            TranslateResponse translateResponse = new TranslateResponse();
            Map<String, RuleEngineInput> functionCallsRuleEngineInput = new HashMap<>();
            ActualDebugCodeCreator actualDebugCodeCreator = new ActualDebugCodeCreator("", 0);
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
            code = extractedCodeAndFunctionCode.getExtractedCode();
            CodeSnippetElement firstCodeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(),
                    new HashMap<>(), new HashMap<>());
            List<DagElement> dagElementList = new ArrayList<>();
            Map<String, String> dagElementAndCodeMap = new HashMap<>();
            int linesForFunctions = actualDebugCodeCreator.getLine();
            DagElement firstDagElement = translateUtil.populateAllDagElements(firstCodeSnippetElement, csvInformationList,
                    functionCallsRuleEngineInput, variableMap, arrayMap, dagElementList, dagElementAndCodeMap, linesForFunctions);
            translateResponse.setFirstDagElement(firstDagElement);
            translateResponse.setDagElementList(dagElementList);
            translateResponse.setCodeAndDagElementMap(dagElementAndCodeMap);
            translateResponse.setCommonFunctionCode(extractedCodeAndFunctionCode.getFunctionCode());

            System.out.println("compilation time: " + (System.currentTimeMillis() - startTime) + "ms");

            startTime = System.currentTimeMillis();
            
            // Execute DAG in parallel mode
            System.out.println("Executing DAG in parallel mode");
            executeInParallel(firstDagElement, dagElementList, variableMap, arrayMap);

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

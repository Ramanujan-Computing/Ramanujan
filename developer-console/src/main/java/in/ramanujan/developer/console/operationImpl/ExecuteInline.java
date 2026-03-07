package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant;
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
            List<CsvInformation> csvInformationList = codeRunRequest.getCsvInformationList() != null
                    ? codeRunRequest.getCsvInformationList() : new ArrayList<>();
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

            // Explicitly add an array for each uploaded CSV to the non-thread (root) DagElement.
            // The array name equals the CSV filename title (without extension).
            for (CsvInformation csvInfo : csvInformationList) {
                String arrayName = getCsvArrayName(csvInfo.getFileName());
                if (!arrayName.isEmpty()) {
                    boolean alreadyExists = arrayMap.values().stream().anyMatch(a -> arrayName.equals(a.getName()));
                    if (!alreadyExists) {
                        Array csvArray = new Array();
                        csvArray.setId(UUID.randomUUID().toString());
                        csvArray.setName(arrayName);
                        csvArray.setValues(parseCsvToValues(csvInfo.getData()));
                        firstDagElement.getRuleEngineInput().getArrays().add(csvArray);
                        arrayMap.put(csvArray.getId(), csvArray);
                    }
                }
            }
            translateResponse.setFirstDagElement(firstDagElement);
            translateResponse.setDagElementList(dagElementList);
            translateResponse.setCodeAndDagElementMap(dagElementAndCodeMap);
            translateResponse.setCommonFunctionCode(functionCode);

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

    /**
     * Derives the array name from a CSV filename by stripping the file extension
     * and any leading path components (e.g. "data/employees.csv" -> "employees").
     */
    private String getCsvArrayName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int slashIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String baseName = slashIndex >= 0 ? fileName.substring(slashIndex + 1) : fileName;
        int dotIndex = baseName.lastIndexOf('.');
        return dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
    }

    /**
     * Parses CSV text into a Map keyed by "row_column" indices, matching the
     * format used by CsvImporter.
     */
    private Map<String, Object> parseCsvToValues(String data) {
        Map<String, Object> values = new HashMap<>();
        if (data == null) {
            return values;
        }
        String[] lines = data.split("\\r?\\n");
        int row = 0;
        for (String line : lines) {
            String[] cols = line.split(",");
            int column = 0;
            for (String col : cols) {
                Constant constant = new Constant();
                constant.setValueAndDataType(col.trim());
                values.put(row + "_" + column, constant.getValue());
                column++;
            }
            row++;
        }
        return values;
    }
}

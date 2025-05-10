package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.middleware.base.CodeSnippetElement;
import in.ramanujan.middleware.base.DagElement;
import in.ramanujan.middleware.base.exception.CompilationException;
import in.ramanujan.middleware.base.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.middleware.base.pojo.TranslateResponse;
import in.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.middleware.base.spring.SpringConfig;
import in.ramanujan.middleware.base.utils.TranslateUtil;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.util.*;

import static in.ramanujan.developer.console.operationImpl.ExecutorImpl.createJson;

/**
 * This class contains the core logic from DebugFetcher, excluding debug file and server creation.
 */
public class ExecuteInline implements Operation {
    protected final TranslateUtil translateUtil;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    public ExecuteInline() {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        translateUtil = applicationContext.getBean(TranslateUtil.class);
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

    @Override
    public void execute(List<String> args) throws IOException {
        try {
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

            Queue<DagElement> dagElementQueue = new LinkedList<>();
            dagElementQueue.add(firstDagElement);
            Set<DagElement> dagElements = new HashSet<>();
            for(DagElement dagElement : dagElementList) {
                dagElements.add(dagElement);
            }
            while(true) {
                Set<DagElement> dagElementsWaiting = new HashSet<>();
                while (!dagElementQueue.isEmpty()) {
                    DagElement dagElement = dagElementQueue.poll();
                    if (dagElement.getPreviousElements().isEmpty()) {
                        dagElements.add(dagElement);
                        dagElementQueue.addAll(dagElement.getNextElements());
                        executeDagElement(dagElement, variableMap, arrayMap);
                    } else {
                        boolean allPreviousTraversed = true;
                        for (DagElement previousDagElement : dagElement.getPreviousElements()) {
                            if (!dagElements.contains(previousDagElement)) {
                                allPreviousTraversed = false;
                                break;
                            }
                        }
                        if (allPreviousTraversed) {
                            dagElements.add(dagElement);
                            dagElementQueue.addAll(dagElement.getNextElements());
                            executeDagElement(dagElement, variableMap, arrayMap);
                        } else {
                            dagElementsWaiting.add(dagElement);
                        }
                    }
                }
                if(dagElementsWaiting.isEmpty()) {
                    break;
                } else {
                    dagElementQueue.addAll(dagElementsWaiting);
                }
            }

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

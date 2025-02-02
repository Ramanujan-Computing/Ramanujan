package in.ramanujan.middleware.service;

import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.middleware.base.CodeSnippetElement;
import in.ramanujan.middleware.base.DagElement;
import in.ramanujan.middleware.base.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.middleware.base.pojo.TranslateResponse;
import in.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.middleware.base.utils.TranslateUtil;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import io.vertx.core.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
public class TranslateService {

    @Autowired
    private TranslateUtil translateUtil;

    /*
    * Translate the given code to graph of DagElement.
    * Returns the entry point in the graph. From that point, DAG traversal can be done.
    * code: Code to be translated
    * variableMap: hashMap that maintains the variables seen in the code
    * arrayMap: hashMap that maintains the array seen in the code
    * variableMap and arrayMap are used when we need to return end-result of computation to the end-developer.
    *
    * Heuristic:
    * 1. Convert all the functions to corresponding RuleEngineInput and extract all the non-function code.
    * 2. Get the graph of CodeSnippetElement in the extracted code.
    * 3. Each CodeSnippetElement object has to be converted to DagElement.
    * 4. Return the entry point in the DagElement graph.
    * */
    public Future<TranslateResponse> translate(String code, List<CsvInformation> csvInformationList, Map<String, Variable> variableMap,
                                               Map<String, Array> arrayMap) {
        Future<TranslateResponse> future = Future.future();
        try {
            TranslateResponse translateResponse = new TranslateResponse();
            Map<String, RuleEngineInput> functionCallsRuleEngineInput = new HashMap<>();
            ActualDebugCodeCreator actualDebugCodeCreator = new ActualDebugCodeCreator("", 0);
            ExtractedCodeAndFunctionCode extractedCodeAndFunctionCode =
                    translateUtil.extractCodeWithoutAbstractCodeDeclaration(code, functionCallsRuleEngineInput, actualDebugCodeCreator);
            code = extractedCodeAndFunctionCode.getExtractedCode();
            CodeSnippetElement firstCodeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(),
                    new HashMap<>(), new HashMap<>());
            List<DagElement> dagElementList = new ArrayList<>();
            Map<String, String> dagElementAndCodeMap = new HashMap<>();
            int linesForFunctions = actualDebugCodeCreator.getLine();
            DagElement firstDagElement  = translateUtil.populateAllDagElements(firstCodeSnippetElement, csvInformationList,
                    functionCallsRuleEngineInput, variableMap, arrayMap, dagElementList, dagElementAndCodeMap, linesForFunctions);
            translateResponse.setFirstDagElement(firstDagElement);
            translateResponse.setDagElementList(dagElementList);
            translateResponse.setCodeAndDagElementMap(dagElementAndCodeMap);
            translateResponse.setCommonFunctionCode(extractedCodeAndFunctionCode.getFunctionCode());
            future.complete(translateResponse);
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

}

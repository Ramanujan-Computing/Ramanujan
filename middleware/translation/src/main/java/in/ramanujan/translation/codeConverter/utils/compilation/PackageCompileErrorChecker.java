package in.ramanujan.translation.codeConverter.utils.compilation;


import in.ramanujan.developer.console.model.pojo.PackageRunInput;
import in.ramanujan.translation.codeConverter.constants.CodeToken;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.CodeContainer;
import in.ramanujan.translation.codeConverter.grammar.SimpleCodeCommand;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackageCompileErrorChecker extends CompileErrorChecker{


    public void checkPackageForCompilation(PackageRunInput packageRunInput) throws CompilationException {
        Map<String, List<String>> functionAndArgumentsMap = new HashMap<>();
        Map<String, List<List<String>>> functionCallsAndArgumentsMap = new HashMap<>();
        if(packageRunInput.getHeaderCodes() != null) {
            for(String file : packageRunInput.getHeaderCodes().keySet()) {
                checkFile(file, packageRunInput.getHeaderCodes().get(file), functionAndArgumentsMap, functionCallsAndArgumentsMap);
            }
        }
        checkMainFile(packageRunInput.getMainCode(), functionAndArgumentsMap, functionCallsAndArgumentsMap);

        validateFunctionDeclAndCallInSync(functionAndArgumentsMap, functionCallsAndArgumentsMap);
    }

    private void validateFunctionDeclAndCallInSync(Map<String, List<String>> functionAndArgumentsMap,
                                                   Map<String, List<List<String>>> functionCallsAndArgumentsMap)
            throws CompilationException {
        for(String functionCalled : functionCallsAndArgumentsMap.keySet()) {
            List<String> argumentsInMethodDeclaration = functionAndArgumentsMap.get(functionCalled);
            if(argumentsInMethodDeclaration == null) {
                throw new CompilationException(null, null, "Function " + functionCalled + " not implemented");
            }
            for(List<String> argumentsFromACall : functionCallsAndArgumentsMap.get(functionCalled)) {
                if(argumentsFromACall.size() != argumentsInMethodDeclaration.size()) {
                    throw new CompilationException(null, null, "Function exec call doesnt have same " +
                            "number of required parameters :" + functionCalled);
                }
            }
        }
    }


    private void checkFile(String file, String code, Map<String, List<String>> functionAndArgumentsMap, Map<String,
            List<List<String>>> functionCallsAndArgumentsMap) throws CompilationException {
        String extractedCode = "";

        List<CodeContainer> functionCodeContainers = new ArrayList<>();

        try {
            checkBrackets(code, new ArrayList<>(), new ArrayList<>());

            extractedCode = functionCodeContainerListCreater(code, extractedCode, functionCodeContainers);
            if(!extractedCode.trim().isEmpty()) {
                throw new CompilationException(null, null, "Only methods can be defined in the included files");
            }
            for(CodeContainer codeContainer : functionCodeContainers) {
                functionAndArgumentsMap.put(codeContainer.getPlaceHolder(), codeContainer.getArguments());
                validateFunctionCode(codeContainer);
                populateAllFunctionCallFromThisCode(codeContainer.getCode(), functionCallsAndArgumentsMap);
            }
        } catch (CompilationException e) {
            e.getMessageString().add("in file " + file);
            throw e;
        }
    }

    private void checkMainFile(String mainCode, Map<String, List<String>> functionAndArgumentsMap,
                               Map<String, List<List<String>>> functionCallsAndArgumentsMap) throws CompilationException {
        try {
            checkBrackets(mainCode, new ArrayList<>(), new ArrayList<>());
            checkIfSignatures(mainCode, new ArrayList<>(), new ArrayList<>());
            checkWhileSignatures(mainCode, new ArrayList<>(), new ArrayList<>());
            checkThreadManagements(mainCode, new ArrayList<>(), new ArrayList<>());

            List<CodeContainer> functionCodeContainers = new ArrayList<>();
            functionCodeContainerListCreater(mainCode, "", functionCodeContainers);

            for (CodeContainer codeContainer : functionCodeContainers) {
                functionAndArgumentsMap.put(codeContainer.getPlaceHolder(), codeContainer.getArguments());
            }
            populateAllFunctionCallFromThisCode(mainCode, functionCallsAndArgumentsMap);

        } catch (CompilationException e) {
            e.getMessageString().add("in mainFile");
        }

    }

    private void populateAllFunctionCallFromThisCode(String code, Map<String, List<List<String>>> functionCallsAndArgumentsMap) {
        List<Integer> indexes = StringUtils.getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.functionExec, ' ');
        for(Integer index : indexes) {
            SimpleCodeCommand simpleCodeCommand = StringUtils.parseForSimpleCodeCommand(CodeToken.functionExec,
                    code.substring(index), new IndexWrapper(0));
            List<List<String>> arguementByDifferentCalls = functionCallsAndArgumentsMap.get(simpleCodeCommand.getPlaceHolder());
            if(arguementByDifferentCalls == null) {
                arguementByDifferentCalls = new ArrayList<>();
                functionCallsAndArgumentsMap.put(simpleCodeCommand.getPlaceHolder(), arguementByDifferentCalls);
            }
            arguementByDifferentCalls.add(simpleCodeCommand.getArguments());
        }
    }

    private void validateFunctionCode(CodeContainer codeContainer) throws CompilationException {
        checkIfSignatures(codeContainer.getCode(), new ArrayList<>(), new ArrayList<>());
        checkWhileSignatures(codeContainer.getCode(), new ArrayList<>(), new ArrayList<>());
    }

    private String functionCodeContainerListCreater(String code, String extractedCode, List<CodeContainer> functionCodeContainers) {
        int lastIndex = 0;
        int iteration = 0;
        List<Integer> allInstaces = StringUtils.getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.functionDef, ' ');
        while (iteration < allInstaces.size()) {
            int instanceIndex = allInstaces.get(iteration);
            extractedCode = extractedCode + code.substring(lastIndex, instanceIndex);
            IndexWrapper indexWrapper = new IndexWrapper(0);
            functionCodeContainers.add(StringUtils.parseForCodeContainer(CodeToken.functionDef, code.substring(instanceIndex), indexWrapper));
            lastIndex = indexWrapper.getIndex() + instanceIndex;
            iteration++;
        }
        extractedCode = extractedCode + code.substring(lastIndex);
        return extractedCode;
    }


}

package in.ramanujan.translation.codeConverter.utils;

import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.codeConverterLogicImpl.VariableInitLogicConverter;
import in.ramanujan.translation.codeConverter.constants.CodeToken;
import in.ramanujan.translation.codeConverter.grammar.CodeContainer;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.pojo.PairCodeSnippetElementWithParent;
import in.ramanujan.translation.codeConverter.pojo.StringWrapper;
import in.ramanujan.translation.codeConverter.exception.CompilationException;

import java.util.*;

public class TranslateUtil {

    private final CodeConverterLogicFactory codeConverterLogicFactory = new CodeConverterLogicFactory();

    /*
    * Traverses the graph of CodeSnippetElement object, and convert each CodeSnippetElement object to DagElement object,
    * in the process, making the graph of it. Returns the entry point to the graph created
    * */
    public DagElement populateAllDagElements(CodeSnippetElement codeSnippetElement, List<CsvInformation> csvInformationList,
                                             Map<String, RuleEngineInput> functionCallsRuleEngineInput,
                                             Map<String, Variable> variableMap, Map<String, Array> arrayMap,
                                             List<DagElement> dagElementListToBePopulated,
                                             Map<String, String> dagElementAndCodeMap, int linesForCommonFunctions) throws CompilationException {
        Queue<PairCodeSnippetElementWithParent> populationQueue = new LinkedList<PairCodeSnippetElementWithParent>();
        CodeConverter codeConverter = getNewCodeConverter(csvInformationList);
        populationQueue.add(new PairCodeSnippetElementWithParent(codeSnippetElement, null));
        DagElement lastElement = null;
        Map<String, DagElement> codeSnippetElementDagElementMap = new HashMap<>();
        while(populationQueue.size() > 0) {
            PairCodeSnippetElementWithParent pairCodeSnippetElementWithParent = populationQueue.poll();
            DagElement dagElement = codeSnippetElementDagElementMap.get(pairCodeSnippetElementWithParent
                    .getCodeSnippetElement().getUuid());
            if(dagElement == null) {
                RuleEngineInput ruleEngineInput = new RuleEngineInput();

                final ActualDebugCodeCreator actualDebugCodeCreator = new ActualDebugCodeCreator("", linesForCommonFunctions);
                codeConverter.interpret(
                        pairCodeSnippetElementWithParent.getCodeSnippetElement().getCode(), ruleEngineInput,
                        new LinkedList<String>() {{add("");}},
                        actualDebugCodeCreator, null, null);

               dagElement = new DagElement(ruleEngineInput);
               dagElementAndCodeMap.put(dagElement.getId(), actualDebugCodeCreator.getDebugCode());

               if(ruleEngineInput.getCommands().size() > 0) {
                   dagElement.setFirstCommandId(ruleEngineInput.getCommands().get(0).getId());
               } else {
                   dagElement.setFirstCommandId("");
               }

                for(RuleEngineInput ruleEngineInputFunction : functionCallsRuleEngineInput.values()) {
                    ruleEngineInput.addAllPartsOfGivenRuleEngineInput(ruleEngineInputFunction);
                }

            }
            DagElement parentDagElement = pairCodeSnippetElementWithParent.getDagElement();
            if(parentDagElement != null) {
                /*
                * Connects the previous dagElement with the current dagElement
                * */
                dagElement.getPreviousElementIds().add(parentDagElement.getId());
                dagElement.getPreviousElements().add(parentDagElement);
                parentDagElement.getNextElements().add(dagElement);
            }
            if(!codeSnippetElementDagElementMap.containsKey(pairCodeSnippetElementWithParent.getCodeSnippetElement().getUuid())) {
                for (CodeSnippetElement childCodeSnippetElement : pairCodeSnippetElementWithParent.getCodeSnippetElement().getNext()) {
                    populationQueue.add(new PairCodeSnippetElementWithParent(childCodeSnippetElement, dagElement));
                }
            }
            codeSnippetElementDagElementMap.put(pairCodeSnippetElementWithParent.getCodeSnippetElement().getUuid(), dagElement);
            variableMap.putAll(dagElement.getVariableMap());
            arrayMap.putAll(dagElement.getArrayMap());
            lastElement = dagElement;


        }
        return  DagUtils.getFirstElementOfDag(lastElement, dagElementListToBePopulated);
    }

    public CodeConverter getNewCodeConverter(List<CsvInformation> csvInformationList) {
        return new CodeConverter(codeConverterLogicFactory, null, csvInformationList);
    }

    private Boolean validateIfSuffixOfMethod(Character c) {
        return (!Character.isAlphabetic(c) && !Character.isDigit(c));
    }

    private String getToBeConsideredToken(String code, IndexWrapper threadStartCodeIndex, IndexWrapper threadEndCodeIndex) {
        String toBeConsidered = "";
        if(threadEndCodeIndex.getIndex() == -1) {
            toBeConsidered = CodeToken.threadStart;
        } else {
            if(threadStartCodeIndex.getIndex() == -1) {
                toBeConsidered = CodeToken.threadTriggerOnSomeThreadCompleteion;
            } else {
                if(threadStartCodeIndex.getIndex() < threadEndCodeIndex.getIndex()) {
                    toBeConsidered = CodeToken.threadStart;
                } else {
                    toBeConsidered = CodeToken.threadTriggerOnSomeThreadCompleteion;
                }
            }
        }


        if(CodeToken.threadStart.equals(toBeConsidered)) {
            Boolean flag = true;
            int tmpIndex = threadStartCodeIndex.getIndex() + CodeToken.threadStart.length();
            if(threadStartCodeIndex.getIndex() > 0 && !validateIfSuffixOfMethod(code.charAt(threadStartCodeIndex.getIndex() - 1))) {
                toBeConsidered ="";
                threadStartCodeIndex.setIndex(code.indexOf(CodeToken.threadStart, threadStartCodeIndex.getIndex() + 1));
                flag = false;
            }
            while(flag && code.charAt(tmpIndex) != '(') {
                if(code.charAt(tmpIndex) != ' ') {
                    toBeConsidered ="";
                    threadStartCodeIndex.setIndex(code.indexOf(CodeToken.threadStart, threadStartCodeIndex.getIndex() + 1));
                    break;
                }
                tmpIndex++;
            }
        } else {
            Boolean flag = true;
            int tmpIndex = threadEndCodeIndex.getIndex() + CodeToken.threadTriggerOnSomeThreadCompleteion.length();
            if(threadEndCodeIndex.getIndex() > 0 && !validateIfSuffixOfMethod(code.charAt(threadEndCodeIndex.getIndex() - 1))) {
                toBeConsidered ="";
                threadEndCodeIndex.setIndex(code.indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion, threadEndCodeIndex.getIndex() + 1));
                flag = false;
            }
            while(flag && code.charAt(tmpIndex) != '(') {
                if(code.charAt(tmpIndex) != ' ') {
                    toBeConsidered ="";
                    threadEndCodeIndex.setIndex(code.indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion, threadEndCodeIndex.getIndex() + 1));
                    break;
                }
                tmpIndex++;
            }
        }

        return toBeConsidered;
    }

    private void parseThreadStartCode(String code, StringWrapper extractedCode, IndexWrapper indexWrapper, int threadStartCodeIndex,
                                      Map<String, CodeSnippetElement> threadCodeSnippetMap,
                                      Map<String, List<CodeSnippetElement>> mappingToBeResolved,
                                      Map<String, List<CodeSnippetElement>> cloningToBeResolved,
                                      CodeSnippetElement codeSnippetElement) {
        //threadStart block to be covered
        if(indexWrapper.getIndex() != threadStartCodeIndex) {
            extractedCode.concat(code.substring(indexWrapper.getIndex(), threadStartCodeIndex).trim());
        }
        indexWrapper.setIndex(threadStartCodeIndex);

        CodeContainer codeContainer = StringUtils.parseForCodeContainer(CodeToken.threadStart, code.substring(indexWrapper.getIndex()), indexWrapper);
        CodeSnippetElement childSnippet = getCodeSnippets(codeContainer.getCode(), threadCodeSnippetMap, mappingToBeResolved, cloningToBeResolved);
        String threadName = codeContainer.getArguments().get(0);
        threadCodeSnippetMap.put(threadName, childSnippet);
        codeSnippetElement.getNext().add(childSnippet);
        indexWrapper.setIndex(indexWrapper.getIndex() + threadStartCodeIndex);


        //Put the code into the cloned CodeSnippet
        List<CodeSnippetElement> clonedCodeSnippetElements = cloningToBeResolved.get(threadName);
        if(clonedCodeSnippetElements != null) {
            for(CodeSnippetElement clonedSnippet : clonedCodeSnippetElements) {
                clonedSnippet.setCode(childSnippet.getCode());
            }
        }
        //Resolve the mapping
        List<CodeSnippetElement> mappedCodeSnippets = mappingToBeResolved.get(threadName);
        if(mappedCodeSnippets != null) {
            for(CodeSnippetElement mappedCodeSnippet : mappedCodeSnippets) {
                childSnippet.getNext().add(mappedCodeSnippet);
            }
        }
    }

    private void parseThreadOnCompleteCode(String code, StringWrapper extractedCode, IndexWrapper indexWrapper,
                                           int threadEndCodeIndex,
                                           Map<String, CodeSnippetElement> threadCodeSnippetMap,
                                           Map<String, List<CodeSnippetElement>> mappingToBeResolved,
                                           Map<String, List<CodeSnippetElement>> cloningToBeResolved) {
        //threadEnd block to be covered
        if(indexWrapper.getIndex() != threadEndCodeIndex) {
            extractedCode.concat(code.substring(indexWrapper.getIndex(), threadEndCodeIndex).trim());
        }
        indexWrapper.setIndex(threadEndCodeIndex);

        CodeContainer codeContainer = StringUtils.parseForCodeContainer(CodeToken.threadTriggerOnSomeThreadCompleteion,
                code.substring(indexWrapper.getIndex()), indexWrapper);
        indexWrapper.setIndex(indexWrapper.getIndex() + threadEndCodeIndex);
        CodeSnippetElement childSnippet = getCodeSnippets(codeContainer.getCode(), threadCodeSnippetMap, mappingToBeResolved, cloningToBeResolved);
        List<String> arguments = codeContainer.getArguments().subList(0, codeContainer.getArguments().size() - 1);
        int iterations = Integer.parseInt(codeContainer.getArguments().get(codeContainer.getArguments().size() -1));
        for(int iteration = 1; iteration <= iterations; iteration++) {
            CodeSnippetElement tempCodeSnippetElement = new CodeSnippetElement();
            tempCodeSnippetElement.setCode("");
            if(iteration == iterations) {
                tempCodeSnippetElement = childSnippet;
            }
            for(String argument : arguments) {
                connectDependentThread(threadCodeSnippetMap, mappingToBeResolved,
                        tempCodeSnippetElement, argument, iteration-1);

                if(iteration != iterations) {
                    CodeSnippetElement argumentCodeSnippetElementForNextIteration = new CodeSnippetElement();
                    cloneNewCodeSnippetWithOriginalCodeSnippetThatWillBeCreatedLater(
                            threadCodeSnippetMap, cloningToBeResolved, iteration, argument,
                            argumentCodeSnippetElementForNextIteration);
                    tempCodeSnippetElement.getNext().add(argumentCodeSnippetElementForNextIteration);
                }
            }
        }
    }

    /*
    * threadCodeSnippetMap is the map of threadId and the codeSnippet corresponding to it
    * mappingToBeResolved is the map between the thread and the list of CodeSnippet successor to the given thread.
    * For example: if the codeSnippet has to be triggered when thread t1 is done. But the code of t1 is written after the
    * threadOnEnd(t1) {codeSnippet}. So when t1's codeSnippet is recorded, we can associate the new codeSnippet with the code
    * of the threadOnEnd
    * cloningToBeResolved is the map between the thread and the list of clones of the thread. For example: a thread has to be
    * repeated for some iteration and the code of main thread has not been read by the parser. Then we add the clones of the code
    * in this map. When the required thread's code is taken by the parser, we can associate the cloned codeSnippets with the
    * requried codeSnippet
    * */

    public CodeSnippetElement getCodeSnippets(String code, Map<String, CodeSnippetElement> threadCodeSnippetMap,
                                               Map<String, List<CodeSnippetElement>> mappingToBeResolved, Map<String, List<CodeSnippetElement>> cloningToBeResolved) {
        CodeSnippetElement codeSnippetElement = new CodeSnippetElement();
        String extractedCode = "";
        int index = 0;
        int threadStartCodeIndex = code.indexOf(CodeToken.threadStart);
        int threadEndCodeIndex = code.indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion);
        while(threadEndCodeIndex !=-1 || threadStartCodeIndex != -1) {
            IndexWrapper threadEndCodeIndexWrapper = new IndexWrapper(threadEndCodeIndex);
            IndexWrapper threadStartCodeIndexWrapper = new IndexWrapper(threadStartCodeIndex);
            String toBeConsidered = getToBeConsideredToken(code, threadStartCodeIndexWrapper, threadEndCodeIndexWrapper);
            threadEndCodeIndex = threadEndCodeIndexWrapper.getIndex();
            threadStartCodeIndex = threadStartCodeIndexWrapper.getIndex();
            if("".equals(toBeConsidered)) {
                continue;
            }

            if(CodeToken.threadStart.equalsIgnoreCase(toBeConsidered)) {
                StringWrapper extractedCodeWrapper = new StringWrapper(extractedCode);
                IndexWrapper indexWrapper = new IndexWrapper(index);
                parseThreadStartCode(code, extractedCodeWrapper, indexWrapper, threadStartCodeIndex, threadCodeSnippetMap,
                        mappingToBeResolved, cloningToBeResolved, codeSnippetElement);
                extractedCode = extractedCodeWrapper.getStr();
                index = indexWrapper.getIndex();
            } else {
                StringWrapper extractedCodeWrapper = new StringWrapper(extractedCode);
                IndexWrapper indexWrapper = new IndexWrapper(index);
                parseThreadOnCompleteCode(code, extractedCodeWrapper, indexWrapper, threadEndCodeIndex, threadCodeSnippetMap,
                        mappingToBeResolved, cloningToBeResolved);
                extractedCode = extractedCodeWrapper.getStr();
                index = indexWrapper.getIndex();
            }
            if(index >= code.length() || code.substring(index).indexOf(CodeToken.threadStart) == -1) {
                threadStartCodeIndex = -1;
            } else {
                threadStartCodeIndex = code.substring(index).indexOf(CodeToken.threadStart) + index;
            }
            if(index >= code.length() || code.substring(index).indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion) == -1) {
                threadEndCodeIndex = -1;
            } else {
                threadEndCodeIndex = code.substring(index).indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion) + index;
            }
        }
        if(index < code.length()) {
            extractedCode += code.substring(index).trim();
        }
        codeSnippetElement.setCode(extractedCode);
        return codeSnippetElement;
    }



    private void cloneNewCodeSnippetWithOriginalCodeSnippetThatWillBeCreatedLater(Map<String, CodeSnippetElement> threadCodeSnippetMap, Map<String, List<CodeSnippetElement>> cloningToBeResolved, int iteration, String dependentThread, CodeSnippetElement codeSnippetElement) {
        threadCodeSnippetMap.put(dependentThread + "_" + iteration, codeSnippetElement);
        if(threadCodeSnippetMap.get(dependentThread) != null) {
            codeSnippetElement.setCode(threadCodeSnippetMap.get(dependentThread).getCode());
            return;
        }
        List<CodeSnippetElement> toBeClonedSnippets = cloningToBeResolved.get(dependentThread);
        if(toBeClonedSnippets == null) {
            toBeClonedSnippets = new ArrayList<>();
        }
        toBeClonedSnippets.add(codeSnippetElement);
        cloningToBeResolved.put(dependentThread, toBeClonedSnippets);
    }

    private void connectDependentThread(Map<String, CodeSnippetElement> threadCodeSnippetMap, Map<String,
            List<CodeSnippetElement>> mappingToBeResolved, CodeSnippetElement childCodeSnippetElement, String dependentThread, int iteration) {
        dependentThread =  dependentThread + (iteration<1?"":"_"+iteration);
        if(threadCodeSnippetMap.get(dependentThread) != null) {
            threadCodeSnippetMap.get(dependentThread).getNext().add(childCodeSnippetElement);
        } else {
            List<CodeSnippetElement> list = mappingToBeResolved.get(dependentThread);
            if(list == null) {
                list = new ArrayList<>();
            }
            list.add(childCodeSnippetElement);
            mappingToBeResolved.put(dependentThread, list);
        }
    }


    /*
    * Converts all the functions into corresponding RuleEngineInput object and maintains hashMap between functionName
    * and corresponding RuleEngineInput object.
    * Return the code excluding the function code.
    * code: Given code in which functions have to picked and converted, and remaining code to be returned,
    * functionCallsRuleEngineInputMap: hashMap between the function-name and the corresponding RuleEngineInput object
    *
    * Heuristics:
    * 1. Get all the instances of function-declaration
    * 2. for each instance:
    *   2.1. extract the function from the given index
    *   2.2. call updateFunctionCallRuleEngineInputMap for the extracted-function-code
    * 3. return the remaining code
    * */
    public ExtractedCodeAndFunctionCode extractCodeWithoutAbstractCodeDeclaration(String code,
                                                                                  Map<String, RuleEngineInput> functionCallsRuleEngineInputMap, ActualDebugCodeCreator actualDebugCodeCreator)
            throws CompilationException {
        List<Integer> allInstaces = StringUtils.getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.functionDef, ' ');
        String extractedCode = "";
        int lastIndex = 0;
        int iteration = 0;

        while(iteration < allInstaces.size()) {
            int instanceIndex = allInstaces.get(iteration);
            extractedCode = extractedCode + code.substring(lastIndex, instanceIndex);
            IndexWrapper indexWrapper = new IndexWrapper(instanceIndex);
            updateFunctionCallRuleEngineInputMap(code, indexWrapper,
                    functionCallsRuleEngineInputMap, actualDebugCodeCreator);
            lastIndex = indexWrapper.getIndex();
            iteration++;
        }
        extractedCode = extractedCode + code.substring(lastIndex, code.length());
        ExtractedCodeAndFunctionCode extractedCodeWithoutAbstractCodeDeclaration =
                new ExtractedCodeAndFunctionCode();
        extractedCodeWithoutAbstractCodeDeclaration.setExtractedCode(extractedCode);
        extractedCodeWithoutAbstractCodeDeclaration.setFunctionCode(actualDebugCodeCreator.getDebugCode());
        return extractedCodeWithoutAbstractCodeDeclaration;
    }

    /*
    * Converts the function code to corresponding RuleEngineInput object.
    * code: The whole code submitted
    * indexWrapper1: index from where function starts
    * functionCallsRuleEngineInputMap: hashMap between functionName and the RuleEngineInput object corresponding to it
    *
    * Heuristic:
    * 1. Get the information(codeContainer) of the given function
    * 2. convert the function-code using the codeConverter.interpret method
    * */
    private void updateFunctionCallRuleEngineInputMap(String code, IndexWrapper indexWrapper1,
                                                      Map<String, RuleEngineInput> functionCallsRuleEngineInputMap,
                                                      ActualDebugCodeCreator debugCodeCreator)
            throws CompilationException{


        IndexWrapper codeContainerIndex = new IndexWrapper(0);
        CodeContainer functionCodeInformation = StringUtils.parseForCodeContainer(CodeToken.functionDef,
                code.substring(indexWrapper1.getIndex()), codeContainerIndex);

        CodeConverter codeConverter = getNewCodeConverter(new ArrayList<>());
        String functionCode = functionCodeInformation.getCode();
        RuleEngineInput ruleEngineInput = functionCallsRuleEngineInputMap.get(functionCode);
        List<String> arguments = functionCodeInformation.getArguments();
        String functionName = functionCodeInformation.getPlaceHolder();
        if(ruleEngineInput == null) {
            ruleEngineInput = new RuleEngineInput();
        }
        List<String> variableScope = new LinkedList<>();
        variableScope.add("func_" + functionCodeInformation.getPlaceHolder());

        VariableInitLogicConverter variableInitLogicConverter = new VariableInitLogicConverter();
        
        Map<Integer, RuleEngineInputUnits> variableFrameMap = new HashMap<>();
        int counter = 0;
        for(String argumentCode : arguments) {
            RuleEngineInputUnits ruleEngineInputs = variableInitLogicConverter.convertCode(argumentCode, ruleEngineInput, codeConverter, variableScope, new NoConcatImpl(), null, null);
            if(ruleEngineInputs instanceof Variable) {
                ((Variable) ruleEngineInputs).setFrameCount(counter);
            }
            if(ruleEngineInputs instanceof Array) {
                ((Array) ruleEngineInputs).setFrameCount(counter);
            }
            variableFrameMap.put(counter, ruleEngineInputs);
            counter++;
        }
        final Integer[] counterId = {counter};
        debugCodeCreator.concat(CodeToken.functionDef + " " + functionName + "("
                + getCommaSeperatedArgs(arguments) + ") {");
        debugCodeCreator.addIndentation();
        debugCodeCreator.nextLine();
        List<Command> functionCommandList = codeConverter.interpret(functionCode, ruleEngineInput, variableScope, debugCodeCreator, variableFrameMap, counterId);
        RuleEngineUtils.addFunctionCall(ruleEngineInput, functionName, arguments, codeConverter, functionCommandList, variableScope, variableFrameMap);
        functionCallsRuleEngineInputMap.put(functionName, ruleEngineInput);
        debugCodeCreator.decrementIndentation();
        debugCodeCreator.concat("}");
        debugCodeCreator.nextLine();

        indexWrapper1.setIndex(indexWrapper1.getIndex() + codeContainerIndex.getIndex());

    }

    private String getCommaSeperatedArgs(final List<String> arguments) {
        String str = "";
        int size = arguments.size();
        for(int i=0; i<size-1;i++) {
            str += (arguments.get(i) + ",");
        }
        str += arguments.get(size - 1);
        return str;
    }

}

package in.ramanujan.translation.codeConverter;


import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.constants.CodeToken;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.CodeContainer;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.utils.CodeConversionUtils;
import in.ramanujan.translation.codeConverter.utils.StringUtils;

import java.util.*;

public class CodeConverter {

    private Map<String, Variable> variableMap ;
    private Map<String, Array> arrayMap;
    private Map<String, String> csvDataMap;
    
    // Frame management for variable ordering like CPython
    private List<RuleEngineInputUnits> localFrame = new ArrayList<>();
    private List<RuleEngineInputUnits> globalFrame = new ArrayList<>();
    private Map<String, Integer> localFrameSequenceMap = new HashMap<>();
    private Map<String, Integer> globalFrameSequenceMap = new HashMap<>();
//    public Variable getVariable(String variableName) {
//        Variable variable = variableMap.get(variableName);
//        return variable;
//    }
//
//    public Array getArray(String arrayName) {
//        return arrayMap.get(arrayName.split("\\[")[0]);
//    }
//
    public void setVariable(Variable variable, String variableScope) {
        variableMap.put(variableScope + variable.getName(), variable);
        // Add to appropriate frame based on scope
        addToFrame(variable, variableScope);
    }

    public void setArray(Array array, String variableScope) {
        arrayMap.put(variableScope + array.getName(), array);
        // Add to appropriate frame based on scope
        addToFrame(array, variableScope);
    }
    
    /**
     * Adds a variable or array to the appropriate frame (local or global)
     * and sets the sequence number.
     */
    private void addToFrame(RuleEngineInputUnits unit, String variableScope) {
        if (variableScope == null || variableScope.isEmpty()) {
            // Global scope
            int sequence = globalFrame.size();
            globalFrame.add(unit);
            globalFrameSequenceMap.put(unit.getId(), sequence);
            
            if (unit instanceof Variable) {
                ((Variable) unit).setGlobalSequence(sequence);
                ((Variable) unit).setLocalSequence(-1);
            } else if (unit instanceof Array) {
                ((Array) unit).setGlobalSequence(sequence);
                ((Array) unit).setLocalSequence(-1);
            }
        } else {
            // Local scope
            int sequence = localFrame.size();
            localFrame.add(unit);
            localFrameSequenceMap.put(unit.getId(), sequence);
            
            if (unit instanceof Variable) {
                ((Variable) unit).setLocalSequence(sequence);
                ((Variable) unit).setGlobalSequence(-1);
            } else if (unit instanceof Array) {
                ((Array) unit).setLocalSequence(sequence);
                ((Array) unit).setGlobalSequence(-1);
            }
        }
    }
    
    /**
     * Creates a new local frame for function calls.
     * Saves current local frame state and creates a new one.
     */
    public void pushLocalFrame() {
        // For simplicity, we'll create a new local frame
        // In a more complex implementation, we might maintain a stack of frames
        localFrame = new ArrayList<>();
        localFrameSequenceMap = new HashMap<>();
    }
    
    /**
     * Removes scope from frames when scope is removed.
     */
    public void popLocalFrame() {
        localFrame.clear();
        localFrameSequenceMap.clear();
    }
    
    /**
     * Gets the local frame list.
     */
    public List<RuleEngineInputUnits> getLocalFrame() {
        return localFrame;
    }
    
    /**
     * Gets the global frame list.
     */
    public List<RuleEngineInputUnits> getGlobalFrame() {
        return globalFrame;
    }


    public String getCsvData(String fileName) {
        if(csvDataMap == null) {
            return null;
        }
        return csvDataMap.get(fileName);
    }

    public CodeConverter(CodeConverterLogicFactory codeConverterLogicFactory, StringUtils stringUtils,
                         List<CsvInformation> csvInformations) {
        this(codeConverterLogicFactory, stringUtils);
        if(csvInformations != null) {
            this.csvDataMap = new HashMap<>();
            for(CsvInformation csvInformation : csvInformations) {
                this.csvDataMap.put(csvInformation.getFileName(), csvInformation.getData());
            }
        }
    }

    public CodeConverter(CodeConverterLogicFactory codeConverterLogicFactory, StringUtils stringUtils) {
        variableMap = new HashMap<>();
        arrayMap = new HashMap<>();
    }

    public Map<String, Variable> getVariableMap() {
        return variableMap;
    }

    public Map<String, Array> getArrayMap() {
        return arrayMap;
    }

    public void setVariableMap(Map<String, Variable> map) {
        this.variableMap = map;
    }

    public void setArrayMap(Map<String, Array> map) {
        this.arrayMap = map;
    }

    public List<Command> interpret(String code, RuleEngineInput ruleEngineInput, List<String> variableScope,
                                   DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap,
                                   Integer[] frameVariableCounterId) throws CompilationException {
        List<String> codeChunks = getCodeChunks(code);
        Command previousCommand = null;
        List<Command> commandInThisCodeChunk = new ArrayList<>();
        for(String codeChunk : codeChunks) {
            String chunkType = getTypeOfChunk(codeChunk);
            Command command = new Command();

            command.setId("command_" + UUID.randomUUID().toString());
            ruleEngineInput.getCommands().add(command);
            commandInThisCodeChunk.add(command);
            CodeConverterLogic codeConverterLogic = CodeConverterLogicFactory.getCodeConverterLogicImpl(chunkType, codeChunk);
            RuleEngineInputUnits ruleEngineInputUnits = null;
            if(codeConverterLogic == null) {
                CodeConversionUtils.useVariable(ruleEngineInput, codeChunk, command, variableMap, arrayMap, variableScope);
            } else {
                command.setCodeStrPtr(debugLevelCodeCreator.getLine());
                ruleEngineInputUnits = codeConverterLogic
                        .convertCode(codeChunk, ruleEngineInput, this, variableScope, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId);

                codeConverterLogic.populateCommand(command, ruleEngineInputUnits);
                if(functionFrameVariableMap != null && (ruleEngineInputUnits instanceof  Variable || ruleEngineInputUnits  instanceof Array)) {
                    functionFrameVariableMap.put(frameVariableCounterId[0], ruleEngineInputUnits);
                    if(ruleEngineInputUnits instanceof  Variable) {
                        ((Variable) ruleEngineInputUnits).setFrameCount(frameVariableCounterId[0]);
                    }
                    if(ruleEngineInputUnits instanceof Array) {
                        ((Array) ruleEngineInputUnits).setFrameCount(frameVariableCounterId[0]);
                    }
                    frameVariableCounterId[0]++;
                }
            }
            if (previousCommand != null) {
                previousCommand.setNextId(command.getId());
            }
            previousCommand = command;
        }
        return commandInThisCodeChunk;
    }

    public String getTypeOfChunk(String codeChunk) {
        int len = codeChunk.length();
        int index = 0;
        while(index < len && codeChunk.charAt(index) == ' ') {
            index++;
        }
        Set<String> validTokens = new HashSet<>();
        validTokens.add("if");
        validTokens.add("while");
        validTokens.add("var");
        validTokens.add("import_csv");
        validTokens.add(CodeToken.functionExec);
        String token = "";
        while(index < codeChunk.length()) {
            if(validTokens.contains(token) && (codeChunk.charAt(index) == '(' || codeChunk.charAt(index) == ' ')) {
                break;
            }
            token += codeChunk.charAt(index++);
        }
        return token;
    }

    private Boolean validateIfSuffixOfMethod(Character c) {
        return (!Character.isAlphabetic(c) && !Character.isDigit(c));
    }

    /*
    * Return the list of code-chunks in the code.
    * For example:
    *var x:integer;
    * {x}={10};
    * if({x}<{12}){{{x}={{x}+{1]}}}
    *
    * The code above has three code-chunks:
    * [
    * "var x:integer;",
    * "{x}={10};",
    * "if({x}<{12}){{{x}={{x}+{1]}}}"
    * ]
    * */
    public List<String> getCodeChunks(String code) {
        List<String> codeChunks = new ArrayList<>();
        int index = 0;
        List<Integer> ifKeywordList = StringUtils.getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, "if", '(');
        List<Integer> whileKeywordList = StringUtils.getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, "while", '(');

        int ifKeywordListIndex = 0, whileKeywordListIndex = 0;

        while(ifKeywordListIndex < ifKeywordList.size() || whileKeywordListIndex < whileKeywordList.size()) {
            int ifKeywordIndex = ifKeywordListIndex < ifKeywordList.size() ? ifKeywordList.get(ifKeywordListIndex) : -1;
            int whileKeywordIndex = whileKeywordListIndex < whileKeywordList.size() ? whileKeywordList.get(whileKeywordListIndex) : -1;
            String toBeConsidered = "";
            if(ifKeywordIndex == -1) {
                toBeConsidered = "while";
            } else {
                if(whileKeywordIndex == -1) {
                    toBeConsidered = "if";
                } else {
                    if(ifKeywordIndex < whileKeywordIndex) {
                        toBeConsidered = "if";
                    } else {
                        toBeConsidered = "while";
                    }
                }
            }


            if("if".equals(toBeConsidered)) {
                ifKeywordListIndex ++;
                if(index > ifKeywordIndex) {
                    continue;
                }
                addSemiColonSeperatedCommands(code.substring(index, ifKeywordIndex), codeChunks);
                IndexWrapper codeContainerIndex = new IndexWrapper(0);
                CodeContainer codeContainer = StringUtils.parseForIfCodeContainer("if", code.substring(ifKeywordIndex), codeContainerIndex);
                StringBuilder stringBuilder = new StringBuilder("if(").append(codeContainer.getArguments().get(0)).append(") {")
                                .append(codeContainer.getCode()).append("}");
                codeChunks.add(stringBuilder.toString());
                index = codeContainerIndex.getIndex() + ifKeywordIndex;
            }
            if("while".equals(toBeConsidered)) {
                whileKeywordListIndex ++;
                if(index > whileKeywordIndex) {
                    continue;
                }
                addSemiColonSeperatedCommands(code.substring(index, whileKeywordIndex), codeChunks);
                IndexWrapper codeContainerIndex = new IndexWrapper(0);
                StringUtils.parseForCodeContainer("while", code.substring(whileKeywordIndex), codeContainerIndex);
                codeChunks.add(code.substring(whileKeywordIndex, whileKeywordIndex + codeContainerIndex.getIndex()));
                index = codeContainerIndex.getIndex() + whileKeywordIndex;
            }

        }
        if(index < code.length()) {
            addSemiColonSeperatedCommands(code.substring(index), codeChunks);
        }

        return codeChunks;
    }

    private void addSemiColonSeperatedCommands(String codeChunks, List<String> codeChunk) {
        for(String semiColonSeperatedCommands : codeChunks.split(";")) {
            semiColonSeperatedCommands = semiColonSeperatedCommands.trim();
            if(!semiColonSeperatedCommands.isEmpty()) {
                codeChunk.add(semiColonSeperatedCommands);
            }
        }
    }
}

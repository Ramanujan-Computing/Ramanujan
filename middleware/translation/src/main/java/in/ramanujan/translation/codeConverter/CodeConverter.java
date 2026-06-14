package in.ramanujan.translation.codeConverter;


import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.MethodDataTypeAgnosticArg;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.constants.CodeToken;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.CodeContainer;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.utils.CodeConversionUtils;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
// import in.ramanujan.translation.codeConverter.ast.AstParser;
import in.ramanujan.translation.codeConverter.ast.ModuleNode;
import in.ramanujan.translation.codeConverter.ast.PythonAstToRuleEngineInputConverter;
import in.ramanujan.translation.codeConverter.utils.PythonAstInvoker;

import java.util.*;

public class CodeConverter {

    private Map<String, Variable> variableMap ;
    private Map<String, Array> arrayMap;
    private Map<String, MethodDataTypeAgnosticArg> methodDataTypeAgnosticArgMap;
    private Map<String, String> csvDataMap;
    // Maps variable name → [objectHandleId, className] for OOP object tracking
    private Map<String, String> objectHandleMap = new HashMap<>();
    private Map<String, String> objectClassMap = new HashMap<>();
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
    }

    public void setArray(Array array, String variableScope) {
        arrayMap.put(variableScope + array.getName(), array);
    }

    public void setMethodDataTypeAgnosticArgMap(MethodDataTypeAgnosticArg methodDataTypeAgnosticArg, String variableScope) {
        methodDataTypeAgnosticArgMap.put(variableScope + methodDataTypeAgnosticArg.getName(), methodDataTypeAgnosticArg);
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
        methodDataTypeAgnosticArgMap = new HashMap<>();
    }

    public Map<String, Variable> getVariableMap() {
        return variableMap;
    }

    public Map<String, Array> getArrayMap() {
        return arrayMap;
    }

    public Map<String, MethodDataTypeAgnosticArg> getMethodDataTypeAgnosticArgMap() {
        return methodDataTypeAgnosticArgMap;
    }

    public void setVariableMap(Map<String, Variable> map) {
        this.variableMap = map;
    }

    public void setArrayMap(Map<String, Array> map) {
        this.arrayMap = map;
    }

    public void registerObject(String varName, String objectHandleId, String className) {
        objectHandleMap.put(varName, objectHandleId);
        objectClassMap.put(varName, className);
    }

    /** Returns [objectHandleId, className] for the given variable name, or null if not an object. */
    public String[] getObjectInfo(String varName) {
        String handleId = objectHandleMap.get(varName);
        if (handleId == null) return null;
        return new String[]{handleId, objectClassMap.get(varName)};
    }

    public void removeObject(String varName) {
        objectHandleMap.remove(varName);
        objectClassMap.remove(varName);
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
                CodeConversionUtils.useVariable(ruleEngineInput, codeChunk, command, variableMap, arrayMap, methodDataTypeAgnosticArgMap, variableScope, false);
            } else {
                command.setCodeStrPtr(debugLevelCodeCreator.getLine());
                ruleEngineInputUnits = codeConverterLogic
                        .convertCode(codeChunk, ruleEngineInput, this, variableScope, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId);

                codeConverterLogic.populateCommand(command, ruleEngineInputUnits);
            }
            if (previousCommand != null) {
                previousCommand.setNextId(command.getId());
            }
            previousCommand = command;
        }
        return commandInThisCodeChunk;
    }

    /**
     * Interprets Python code using AST-based parsing instead of string-based parsing.
     * 
     * @param pythonCode The Python code to interpret
     * @param ruleEngineInput The RuleEngineInput object to populate
     * @param variableScope List of variable scopes
     * @param debugLevelCodeCreator Debug code creator for generating debug output
     * @param functionFrameVariableMap Map of function frame variables
     * @param frameVariableCounterId Counter for frame variable IDs
     * @return List of Commands created from the Python code
     * @throws CompilationException If there are errors during compilation
     */

    /**
     * Remove common leading whitespace from all non-empty lines (like Python's textwrap.dedent).
     * This handles code extracted from threadStart { ... } blocks that may be indented.
     */
    private static String dedentPythonCode(String code) {
        if (code == null || code.isEmpty()) return code;
        String[] lines = code.split("\n", -1);
        // Find minimum indentation of non-empty lines
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            int indent = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') indent++;
                else if (line.charAt(i) == '\t') indent += 4;
                else break;
            }
            minIndent = Math.min(minIndent, indent);
        }
        if (minIndent == 0 || minIndent == Integer.MAX_VALUE) return code;
        // Strip minIndent spaces from the beginning of each line
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            if (lines[i].trim().isEmpty()) {
                sb.append(lines[i]);
            } else {
                int removed = 0;
                int j = 0;
                while (j < lines[i].length() && removed < minIndent) {
                    if (lines[i].charAt(j) == ' ') { removed++; j++; }
                    else if (lines[i].charAt(j) == '\t') { removed += 4; j++; }
                    else break;
                }
                sb.append(lines[i].substring(j));
            }
        }
        return sb.toString();
    }

    public List<Command> interpretPython(String pythonCode, RuleEngineInput ruleEngineInput, 
                                        List<String> variableScope,
                                        DebugLevelCodeCreator debugLevelCodeCreator, 
                                        Map<Integer, RuleEngineInputUnits> functionFrameVariableMap,
                                        Integer[] frameVariableCounterId) throws CompilationException {
        try {
            // Dedent the code: strip common leading whitespace so code extracted
            // from threadStart { ... } blocks doesn't cause IndentationError
            pythonCode = dedentPythonCode(pythonCode);

            // Step 1: Invoke Python ast2json to get AST JSON
            PythonAstInvoker invoker = new PythonAstInvoker();
            String astJson = invoker.invokeAstJson(pythonCode);

//            // Debug: Print AST JSON from Python
//            System.out.println("========== AST JSON FROM PYTHON ==========");
//            System.out.println(astJson);
//            System.out.println("==========================================");

            // Step 2: Parse AST JSON into Java AST objects
            in.ramanujan.translation.codeConverter.ast.JsonAstParser parser = new in.ramanujan.translation.codeConverter.ast.JsonAstParser();
            ModuleNode module = parser.parseJson(astJson);
            
//            // Debug: Print parsed Module toString
//            System.out.println("========== PARSED MODULE (toString) ==========");
//            System.out.println(module.toString());
//            System.out.println("===============================================");
            
            // Step 3: Convert AST to RuleEngineInput
            System.out.println("========== STARTING AST TO RULE ENGINE CONVERSION ==========");
            PythonAstToRuleEngineInputConverter converter = new PythonAstToRuleEngineInputConverter(
                this, ruleEngineInput, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId
            );

            variableScope.add("");

            List<Command> commands = converter.convert(module, variableScope);
            System.out.println("========== AST TO RULE ENGINE CONVERSION COMPLETE ==========");
            System.out.println("Commands created: " + commands.size());
            
            return commands;
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new CompilationException(null, null, "Error parsing Python code: " + e.getMessage());
        }
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

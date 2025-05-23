package in.ramanujan.translation.codeConverter.utils;


import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayCommand;
import in.ramanujan.translation.codeConverter.exception.CompilationException;

import java.util.*;

public class CodeConversionUtils {
    public static String useVariable(RuleEngineInput ruleEngineInput, String codeChunk, Command command,
                                     Map<String, Variable> variableMap, Map<String, Array> arrayMap, List<String> variableScope)
            throws CompilationException {
        Variable variable = getVariable(variableMap, codeChunk.trim(), variableScope);
        if (variable != null) {
            command.setVariableId(variable.getId());
            addVariableInRuleEngineInput(ruleEngineInput, variable);
            return variable.getId();
        } else {
            Array array = getArray(arrayMap, codeChunk, variableScope);
            if (array != null) {
                ArrayCommand arrayCommand = new ArrayCommand();
                arrayCommand.setArrayId(array.getId());
                arrayCommand.setIndex(getIndexesOfArray(codeChunk, ruleEngineInput, command, variableMap, arrayMap, variableScope));
                command.setArrayCommand(arrayCommand);
                addArrayInRuleEngineInput(ruleEngineInput, array);
                return array.getId();
            } else {
                if (codeChunkContainsSpecialCharacter(codeChunk)) {
                    throw new CompilationException(null, null, "Invalid code tokens: " + codeChunk);
                }
                Constant constant = new Constant();
                constant.setId(UUID.randomUUID().toString());
                constant.setValueAndDataType(codeChunk);
                ruleEngineInput.getConstants().add(constant);
                command.setConstant(constant.getId());
                return constant.getId();
            }
        }
    }

    private static boolean codeChunkContainsSpecialCharacter(String codeChunk) {
        HashSet<Character> characterList = new HashSet<Character>(){{add(' ');add('[');
            add(']');add('{');add('}');add(',');add(';');add('(');add(')');}};
        if(codeChunk == null) {
            return  false;
        }
        for (int i = 0; i< codeChunk.length(); i++) {
            if(characterList.contains(codeChunk.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static Variable getVariable(Map<String, Variable> variableMap, String variableName, List<String> variableScope) {
        Variable variable;
        for(String scope : variableScope) {
            variable = variableMap.get(scope + variableName);
            if(variable != null) {
                return variable;
            }
        }
        return null;
    }

    public static Array getArray(Map<String, Array> arrayMap, String arrayName, List<String> variableScope) {
        arrayName = arrayName.split("\\[")[0];
        if (arrayName != null) {
            arrayName.trim();
        }
        Array array;
        for(String scope : variableScope) {
            array = arrayMap.get(scope + arrayName);
            if(array != null) {
                return array;
            }
        }
        return null;
    }

    private static List<String> getIndexesOfArray(String arrayCodeChunk, RuleEngineInput ruleEngineInput, Command command,
                                                  Map<String, Variable> variableMap, Map<String, Array> arrayMap,
                                                  List<String> variableScope) throws CompilationException{
        List<String> indexStringList = new ArrayList<>();
        int len = arrayCodeChunk.length();
        for (int i = 0; i < len; i++) {
            if (arrayCodeChunk.charAt(i) == '[') {
                String indexString = "";
                for (int j = i + 1; j < len; j++) {
                    if (arrayCodeChunk.charAt(j) == ']') {
                        i = j;
                        indexStringList.add(indexString);
                        break;
                    }
                    indexString += arrayCodeChunk.charAt(j);
                }

            } else {

            }
        }
        List<String> indexIdList = new ArrayList<>();
        for (String indexString : indexStringList) {
            indexIdList.add(useVariable(ruleEngineInput, indexString, command, variableMap, arrayMap, variableScope));
        }
        return indexIdList;
    }

    private static void addArrayInRuleEngineInput(RuleEngineInput ruleEngineInput, Array array) {
        Boolean toBeAdded = true;
        for (Array arrayInRuleInput : ruleEngineInput.getArrays()) {
            if (array == arrayInRuleInput) {
                toBeAdded = false;
                break;
            }
        }
        if (toBeAdded) {
            ruleEngineInput.getArrays().add(array);
        }
    }

    private static void addVariableInRuleEngineInput(RuleEngineInput ruleEngineInput, Variable variable) {
        boolean toBeAdded = true;
        for (Variable variableInRuleInput : ruleEngineInput.getVariables()) {
            if (variableInRuleInput == variable) {
                toBeAdded = false;
                break;
            }
        }
        if (toBeAdded) {
            ruleEngineInput.getVariables().add(variable);
        }
    }
}

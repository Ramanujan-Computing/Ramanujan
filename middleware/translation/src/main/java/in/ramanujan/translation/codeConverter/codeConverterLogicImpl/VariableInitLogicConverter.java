package in.ramanujan.translation.codeConverter.codeConverterLogicImpl;

import in.ramanujan.enums.DataType;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.MethodDataTypeAgnosticArg;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogic;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.utils.CodeConversionUtils;
import in.ramanujan.utils.Constants;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VariableInitLogicConverter implements CodeConverterLogic {
    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {
        if(ruleEngineInputUnits instanceof RedefineArrayCommand)
        {
            command.setRedefineArrayCommand((RedefineArrayCommand) ruleEngineInputUnits);
        }
    }

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter,
                                            List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator,
                                            Map<Integer, RuleEngineInputUnits> functionFrameVariableMap,
                                            Integer[] frameVariableCounterId) throws CompilationException {
        try {
            code = code.substring(code.indexOf("var") + "var ".length());
            String[] variableNames = code.split(":")[0].split(",");
            String dataType = code.split(":")[1].trim();
            appendInDebugLevelCode(debugLevelCodeCreator, dataType, variableNames);
            if (Constants.array.equalsIgnoreCase(dataType)) {
                Array array = null;
                for (String variableName : variableNames) {
                    String variableNameTrimmed = variableName.trim();
                    String[] variableNameTrimmedWithIndex = variableNameTrimmed.split("\\[");
                    variableName = variableNameTrimmedWithIndex[0].trim();
                    
                    boolean hasNonConstantDimension = false;
                    List<Integer> constantDims = new java.util.ArrayList<>();
                    List<String> resolvedDims = new java.util.ArrayList<>();
                    for(int index = 1; index < variableNameTrimmedWithIndex.length; index++) {
                        String dimStr = variableNameTrimmedWithIndex[index].replace("]", "").trim();
                        try {
                            int dim = Integer.parseInt(dimStr);
                            constantDims.add(dim);
                            resolvedDims.add(dimStr); // keep integer as string
                        } catch (NumberFormatException nfe) {
                            hasNonConstantDimension = true;
                            constantDims.add(1); // Use a placeholder for now
                            // Resolve variable name to variable ID
                            Variable dimVar = CodeConversionUtils.getVariable(
                                codeConverter.getVariableMap(), dimStr, variableScope);
                            if (dimVar != null) {
                                resolvedDims.add(dimVar.getId());
                            } else {
                                String[] methodDataTypeAgnosticArgScopeStr = new String[1];
                                MethodDataTypeAgnosticArg methodDataTypeAgnosticArg = CodeConversionUtils.getMethodDataTypeAgnosticArg(
                                                                            codeConverter.getMethodDataTypeAgnosticArgMap(), dimStr, variableScope, new String[1]
                                );
                                if (methodDataTypeAgnosticArg != null) {
                                    resolvedDims.add(methodDataTypeAgnosticArg.getId());
                                    //Create variable, put in the scope. add in the ruleEngineInput and delete the methodDataTypeAgnosticArgMap
                                    ruleEngineInput.getMethodDataTypeAgnosticArgs().remove(methodDataTypeAgnosticArg);
                                    Variable variableDim = new Variable();
                                    variableDim.setId(methodDataTypeAgnosticArg.getId());
                                    variableDim.setName(methodDataTypeAgnosticArg.getName());

                                    codeConverter.getMethodDataTypeAgnosticArgMap().remove(methodDataTypeAgnosticArgScopeStr[0]);
                                    codeConverter.getVariableMap().put(methodDataTypeAgnosticArgScopeStr[0], variableDim);
                                } else {
                                    throw new CompilationException(null, null, "Dimension variable '" + dimStr + "' not found in scope");
                                }
                            }
                        }
                    }
                    array = new Array();
                    String uuid = UUID.randomUUID().toString();
                    // TODO: have a better way to persist the name of the array. Maybe on database layer, we might need
                    // an extra table to persist the name of the array and its id.
                    array.setId((!variableScope.isEmpty() && !variableScope.get(0).isEmpty()? variableScope.get(variableScope.size() - 1) + uuid: uuid + "_name_" + variableName));
                    array.setName(variableName);
                    array.setDataType(dataType);
                    if(!hasNonConstantDimension) {

                        array.setDimension(constantDims);

                    }
                    ruleEngineInput.getArrays().add(array);
                    
                    codeConverter.setArray(array, variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "");

                    // If any dimension is not a constant, emit RedefineArrayCommand
                    if (hasNonConstantDimension) {
                        in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand redefineCmd = new in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand();
                        redefineCmd.setId(UUID.randomUUID().toString());
                        redefineCmd.setArrayId(array.getId());
                        redefineCmd.setNewDimensions(resolvedDims);
                        return redefineCmd;
                    }

                    populateFrameVariableMap(functionFrameVariableMap, frameVariableCounterId, array);
                }
                return array;
            }
            if(DataType.getDataTypeInfo(dataType) == null) {
                throw new CompilationException(null, null, "wrong datatype: " + dataType);
            }

            Variable variable = null;
            for (String variableName : variableNames) {
                variableName = variableName.trim();
                variable = new Variable();
                variable.setId((variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "") +
                        UUID.randomUUID().toString());
                variable.setName(variableName);
                variable.setDataType(dataType);
                ruleEngineInput.getVariables().add(variable);
                codeConverter.setVariable(variable, variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "");

                populateFrameVariableMap(functionFrameVariableMap, frameVariableCounterId, variable);
            }
            return variable;
        } catch (CompilationException e) {
            e.getMessageString().add("in code :" + code);
            throw e;
        } catch (Exception e) {
            throw new CompilationException(null, null, "Variable initialization has compilation issue: " + code);
        }
    }

    private static void populateFrameVariableMap(Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId, RuleEngineInputUnits ruleEngineInputUnits) {
        if(functionFrameVariableMap != null) {
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

    private void appendInDebugLevelCode(DebugLevelCodeCreator debugLevelCodeCreator, String dataType, String[] variableNames) {
        String str = "var ";
        int size = variableNames.length;
        for(int i=0; i < (size - 1); i++) {
            str += variableNames[i] + ",";
        }
        str += variableNames[size -1] + ":" + dataType + ";";
        debugLevelCodeCreator.concat(str);
    }
}

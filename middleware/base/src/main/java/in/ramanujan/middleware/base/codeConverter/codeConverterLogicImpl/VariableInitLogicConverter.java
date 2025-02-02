package in.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl;

import in.ramanujan.enums.DataType;
import in.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.ramanujan.middleware.base.codeConverter.CodeConverterLogic;
import in.ramanujan.middleware.base.exception.CompilationException;
import in.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.utils.Constants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VariableInitLogicConverter implements CodeConverterLogic {
    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {

    }

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter, List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
        try {
            code = code.substring(code.indexOf("var") + "var ".length());
            String[] variableNames = code.split(":")[0].split(",");
            String dataType = code.split(":")[1].trim();
            appendInDebugLevelCode(debugLevelCodeCreator, dataType, variableNames);
            if (Constants.array.equalsIgnoreCase(dataType)) {
                Array array = null;
                for (String variableName : variableNames) {
                    /*
                    * arr[dim1][dim2]...[dimN]
                    * Developer can give any dimensional array. for ex: arr[4][5][6]: this creates a 3D array, with
                    * first dimension can have 4, second can have 5, third can have 6.
                    */
                    String variableNameTrimmed = variableName.trim();
                    String[] variableNameTrimmedWithIndex = variableNameTrimmed.split("\\[");
                    variableName = variableNameTrimmedWithIndex[0].trim();
                    array = new Array();
                    for(int index = 1; index < variableNameTrimmedWithIndex.length; index++) {
                        array.getDimension().add(Integer.parseInt(variableNameTrimmedWithIndex[index]
                                .replace("]", "").trim()));
                    }
                    array.setId((variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "") +
                            UUID.randomUUID().toString());
                    array.setName(variableName);
                    array.setDataType(dataType);
                    ruleEngineInput.getArrays().add(array);
                    codeConverter.setArray(array, variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "");
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
            }
            return variable;
        } catch (CompilationException e) {
            e.getMessageString().add("in code :" + code);
            throw e;
        } catch (Exception e) {
            throw new CompilationException(null, null, "Variable initialization has compilation issue: " + code);
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

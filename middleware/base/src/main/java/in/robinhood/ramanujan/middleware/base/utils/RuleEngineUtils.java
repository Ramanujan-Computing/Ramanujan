package in.robinhood.ramanujan.middleware.base.utils;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.RuleEngineInputUnits;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RuleEngineUtils {
    public static void addFunctionCall(RuleEngineInput ruleEngineInput, String functionName, List<String> argumentCode,
                                       CodeConverter codeConverter, List<Command> commandList, List<String> variableScope, Map<Integer, RuleEngineInputUnits> variableFrameMap)
            throws CompilationException {
        List<String> arguments = new ArrayList<>();
        for(String argumentDecl : argumentCode) {
            /*
            * var x:integer
            * */
            argumentDecl = argumentDecl.substring(argumentDecl.indexOf("var") + "var".length());
            String variableName = argumentDecl.split(":")[0].trim();
//            RuleEngineInputUnits ruleEngineInputUnits = codeConverter.getVariable(variableName);
//            if(ruleEngineInputUnits == null) {
//                ruleEngineInputUnits = codeConverter.getArray(variableName);
//            }
//            arguments.add(ruleEngineInputUnits.getId());
            arguments.add(CodeConversionUtils.useVariable(ruleEngineInput, variableName, new Command(),
                    codeConverter.getVariableMap(), codeConverter.getArrayMap(), variableScope));
        }
        FunctionCall functionCall = new FunctionCall();
        functionCall.setId(functionName);
        functionCall.setFirstCommandId(commandList.size() > 0 ? commandList.get(0).getId() : null);
        functionCall.setArguments(arguments);

        List<String> variablesInFunction = new ArrayList<>();
        int counter = 0;
        while(true) {
            RuleEngineInputUnits units = variableFrameMap.get(counter);
            if(units == null) {
                break;
            }
            variablesInFunction.add(units.getId());
            counter++;
        }

        functionCall.setAllVariablesInMethod(variablesInFunction);
        ruleEngineInput.getFunctionCalls().add(functionCall);
    }
}

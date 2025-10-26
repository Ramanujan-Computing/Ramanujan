package in.ramanujan.translation.codeConverter.codeConverterLogicImpl;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogic;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.SimpleCodeCommand;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.utils.CodeConversionUtils;
import in.ramanujan.translation.codeConverter.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FunctionLogicConverter implements CodeConverterLogic {

    private final StringUtils stringUtils = new StringUtils();

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter,
                                            List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId)
            throws CompilationException {
        try {
            FunctionCall functionCall = new FunctionCall();
            /*
             * exec funcCall(arg1,arg2)
             * */
            SimpleCodeCommand functionCommand = stringUtils.parseForSimpleCodeCommand("exec", code, new IndexWrapper(0));
            List<String> arguments = functionCommand.getArguments();
            String functionName = functionCommand.getPlaceHolder();
            List<String> argumentIds = new ArrayList<>();
            appendDebugLevelCode(debugLevelCodeCreator, functionName, arguments);

            for (String argument : arguments) {

                String arguementConversionId = CodeConversionUtils.useVariable(ruleEngineInput, argument, new Command(),
                        codeConverter.getVariableMap(), codeConverter.getArrayMap(), codeConverter.getMethodDataTypeAgnosticArgMap(), variableScope);

                argumentIds.add(arguementConversionId);

            }
            functionCall.setId(functionName);
            functionCall.setArguments(argumentIds);
            return functionCall;
        } catch (Exception e) {
            throw new CompilationException(null, null, "Compilation error in function call: " + code);
        }
    }

    private void appendDebugLevelCode(DebugLevelCodeCreator debugLevelCodeCreator, String functionName, List<String> arguments) {
        String str = "exec " + functionName + "(";
        int size = arguments.size();
        for(int i = 0; i < size-1; i++) {
            str += arguments.get(i) + ",";
        }
        str += arguments.get(size - 1) + ");";
        debugLevelCodeCreator.concat(str);
    }

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {
        if(command != null) {
            command.setFunctionCall((FunctionCall) ruleEngineInputUnits);
        }
    }
}

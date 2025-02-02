package in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverterLogic;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.pojo.IndexWrapper;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.SimpleCodeCommand;
import in.robinhood.ramanujan.middleware.base.utils.CodeConversionUtils;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.RuleEngineInputUnits;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FunctionLogicConverter implements CodeConverterLogic {

    @Autowired
    private StringUtils stringUtils;

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
                        codeConverter.getVariableMap(), codeConverter.getArrayMap(), variableScope);

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

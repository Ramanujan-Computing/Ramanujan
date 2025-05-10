package in.ramanujan.translation.codeConverter.codeConverterLogicImpl;

import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogic;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;

import java.util.List;
import java.util.Map;

public class ForLogicConverter implements CodeConverterLogic {
    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter, List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
        return null;
    }

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {

    }
}

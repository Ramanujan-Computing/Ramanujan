package in.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl;

import in.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.ramanujan.middleware.base.codeConverter.CodeConverterLogic;
import in.ramanujan.middleware.base.exception.CompilationException;
import in.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ForLogicConverter implements CodeConverterLogic {
    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter, List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
        return null;
    }

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {

    }
}

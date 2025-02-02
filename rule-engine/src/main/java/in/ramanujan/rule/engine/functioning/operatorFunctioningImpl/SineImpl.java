package in.ramanujan.rule.engine.functioning.operatorFunctioningImpl;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.functioning.OperatorFunctioning;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class SineImpl implements OperatorFunctioning {
    @Override
    public CachedOperationFunctioning process(CommandRE operand1, CommandRE operand2, String processId, ContextStack contextStack, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, DebuggerPoint debuggerPoint) {
        throw new RuntimeException(NOT_IMPL);
//        operand1 = getOperandValueAsPerDataType(operand1, processId, mapBetweenIdAndRuleInput, NoDebugPoint.INSTANCE, contextStack);
//        operand2 = getOperandValueAsPerDataType(operand2, processId, mapBetweenIdAndRuleInput, NoDebugPoint.INSTANCE, contextStack);
//
//        return Math.sin(Double.parseDouble(operand2 + ""));
    }
}

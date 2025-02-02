package in.ramanujan.rule.engine.functioning.operatorFunctioningImpl;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.OperatorFunctioning;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataOperation;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class AssignImpl implements OperatorFunctioning {

    public static  long count = 0;
    public static  long time = 0;
    @Override
    public CachedOperationFunctioning process(CommandRE operand1, CommandRE operand2, String processId, ContextStack contextStack,
                                              Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, DebuggerPoint debuggerPoint) {
        final DataOperation op1 = OperatorFunctioning.getDataContainerRE(operand1, mapBetweenIdAndRuleInput, processId, contextStack, debuggerPoint);
        final DataOperation op2 = OperatorFunctioning.getDataContainerRE(operand2, mapBetweenIdAndRuleInput, processId, contextStack, debuggerPoint);

        return new CachedOperationFunctioning() {
            @Override
            public Object operate() {
                double val = op2.get();
                debuggerPoint.addAfterOp(operand1, val);
                return op1.set(val, processId);
            }
        };
    }
}

package in.ramanujan.rule.engine.functioning.operatorFunctioningImpl;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.OperatorFunctioning;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataOperation;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class DivideImpl implements OperatorFunctioning {
    @Override
    public CachedOperationFunctioning process(CommandRE operand1, CommandRE operand2, String processId, ContextStack contextStack, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, DebuggerPoint debuggerPoint) {
        DataOperation op1= OperatorFunctioning.getDataContainerRE(operand1, mapBetweenIdAndRuleInput, processId, contextStack, debuggerPoint);
        DataOperation op2 = OperatorFunctioning.getDataContainerRE(operand2, mapBetweenIdAndRuleInput, processId, contextStack, debuggerPoint);

        return new CachedOperationFunctioning() {
            @Override
            public Object operate() {
                return op1.divide(op2.get());
            }
        };
    }
}

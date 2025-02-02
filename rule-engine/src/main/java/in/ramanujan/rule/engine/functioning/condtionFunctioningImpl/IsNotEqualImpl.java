package in.ramanujan.rule.engine.functioning.condtionFunctioningImpl;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.ConditionFunctioning;
import in.ramanujan.rule.engine.functioning.OperatorFunctioning;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataOperation;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class IsNotEqualImpl implements ConditionFunctioning {
    @Override
    public CachedConditionFunctioning process(CommandRE compareWhat, CommandRE compareWith, String processId, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, ContextStack contextStack, DebuggerPoint debuggerPoint) {
        DataOperation compareWhatOp = OperatorFunctioning.getDataContainerRE(compareWhat, mapBetweenIdAndRuleInput,
                processId, contextStack, debuggerPoint);
        DataOperation compareWithOp = OperatorFunctioning.getDataContainerRE(compareWith, mapBetweenIdAndRuleInput,
                processId, contextStack, debuggerPoint);

        return new CachedConditionFunctioning() {
            @Override
            public boolean conditionOperate() {
                return compareWhatOp.isNotEqual(compareWithOp.get());
            }
        };
    }
}

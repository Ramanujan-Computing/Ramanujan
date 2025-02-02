package in.ramanujan.rule.engine.functioning.condtionFunctioningImpl;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.ConditionFunctioning;
import in.ramanujan.rule.engine.manager.ConditionManager;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class OrImpl implements ConditionFunctioning {
    @Override
    public CachedConditionFunctioning process(CommandRE compareWhat, CommandRE compareWith, String processId, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, ContextStack contextStack, DebuggerPoint debuggerPoint) {
        return new CachedConditionFunctioning() {
            @Override
            public boolean conditionOperate() {
                return ConditionManager.process(mapBetweenIdAndRuleInput, compareWhat.getConditionRE(), processId, contextStack, debuggerPoint) ||
                        ConditionManager.process(mapBetweenIdAndRuleInput, compareWhat.getConditionRE(), processId, contextStack, debuggerPoint);
            }
        };
    }
}

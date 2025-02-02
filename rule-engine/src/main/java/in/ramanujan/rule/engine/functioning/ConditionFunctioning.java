package in.ramanujan.rule.engine.functioning;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.condtionFunctioningImpl.CachedConditionFunctioning;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public interface ConditionFunctioning {
    public CachedConditionFunctioning process(CommandRE compareWhat, CommandRE compareWith,
                                              String processId, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput,
                                              ContextStack contextStack, DebuggerPoint debuggerPoint);
}

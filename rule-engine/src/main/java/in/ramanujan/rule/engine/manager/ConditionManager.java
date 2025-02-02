package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.checkpointing.CheckpointPusher;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.condtionFunctioningImpl.CachedConditionFunctioning;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.ConditionRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class ConditionManager {
    public static Boolean process(Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, ConditionRE condition,
                                  String processId, ContextStack contextStack,
                                  DebuggerPoint debuggerPoint) {
        CachedConditionFunctioning conditionFunctioning = condition.getCachedConditionFunctioning();
        if(conditionFunctioning == null) {
            conditionFunctioning = condition.getConditionFunctioning()
                    .process(condition.getComparisionCommand1(), condition.getComparisionCommand2(), processId,
                            mapBetweenIdAndRuleInput, contextStack, debuggerPoint);
            condition.setCachedConditionFunctioning(conditionFunctioning);
        }
        boolean result = conditionFunctioning.conditionOperate();
        debuggerPoint.setConditionVal(result);
        return result;
    }
}

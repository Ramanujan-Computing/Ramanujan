package in.ramanujan.rule.engine.functioning;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.checkpointing.CheckpointPusher;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public interface CommandFunction {
    public Object operate(String processId, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput,
                          DebuggerPoint debuggerPointContributed, ContextStack contextStack, Checkpoint checkpoint, CheckpointPusher checkpointPusher, Boolean toBeDebuuged);
}

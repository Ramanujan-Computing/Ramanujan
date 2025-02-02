package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.checkpointing.CheckpointPusher;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.debugger.DebuggerValueManager;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.IfRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class IfManager {
    public static void process(Map<String, RuleEngineInputUnit> ruleEngineInputUnitMap, IfRE ifRE, String processId,
                               ContextStack contextStack, Checkpoint checkpoint, Boolean toBeDebugged, String callingCommandId, CheckpointPusher checkpointPusher) {
        DebuggerPoint debuggerPoint = NoDebugPoint.INSTANCE;
        if(toBeDebugged) {
            debuggerPoint = new DebuggerPoint();
            debuggerPoint.setCommandId(callingCommandId);
            debuggerPoint.setLine(ruleEngineInputUnitMap.get(callingCommandId).getCodeStrPtr());
            DebuggerValueManager.addDebugger(processId, debuggerPoint);
        }
        Boolean condition = ConditionManager.process(ruleEngineInputUnitMap, ifRE.getConditionRE(), processId, contextStack,
                debuggerPoint);
        CommandRE command = null;
        if(condition) {
            command = ifRE.getIfCommandRE();
            //CommandManager.process(mapBetweenIdAndRuleInput, (Command) mapBetweenIdAndRuleInput.get(ifBlock.getIfCommand()), processId, contextStack, checkpoint);
        } else {
            command = ifRE.getElseCommandRE();
            //CommandManager.process(mapBetweenIdAndRuleInput, (Command) mapBetweenIdAndRuleInput.get(ifBlock.getElseCommandId()), processId, contextStack, checkpoint);
        }
        while (command != null) {
            CommandManager.process(ruleEngineInputUnitMap, command, processId, contextStack, checkpoint, toBeDebugged, NoDebugPoint.INSTANCE, checkpointPusher);
            command = command.getNextCommand();
        }
    }
}

package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.checkpointing.CheckpointPusher;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.debugger.DebuggerValueManager;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.WhileRE;

import java.util.Date;
import java.util.Map;

public class WhileManager {
    public static void process(Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, WhileRE whileBlock,
                               String processId, ContextStack contextStack, Checkpoint checkpoint, Boolean toBeDebugged, String callingCommandId, CheckpointPusher checkpointPusher) {
        while(checkCondition(mapBetweenIdAndRuleInput, whileBlock, processId, contextStack, checkpoint, toBeDebugged, callingCommandId, checkpointPusher)) {
            CommandRE commandRE = whileBlock.getWhileCommand();
            while(commandRE != null) {
                CommandManager.process(mapBetweenIdAndRuleInput, commandRE, processId, contextStack, checkpoint, toBeDebugged, NoDebugPoint.INSTANCE, checkpointPusher);
                commandRE = commandRE.getNextCommand();
            }
        }
    }

    private static Boolean checkCondition(Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, WhileRE whileBlock, String processId, ContextStack contextStack, Checkpoint checkpoint, Boolean toBeDebugged, String callingCommandId, CheckpointPusher checkpointPusher) {
        Boolean result =  ConditionManager.process(mapBetweenIdAndRuleInput, whileBlock.getCondition(), processId, contextStack,
                getNewDebugPoint(processId, toBeDebugged, callingCommandId, mapBetweenIdAndRuleInput));
        return result;
    }

    private static DebuggerPoint getNewDebugPoint(String processId, Boolean toBeDebugged, String callingCommandId, Map<String, RuleEngineInputUnit> ruleEngineInputUnitMap) {
        if(toBeDebugged) {
            DebuggerPoint debuggerPoint = new DebuggerPoint();
            debuggerPoint.setCommandId(callingCommandId);
            debuggerPoint.setLine(ruleEngineInputUnitMap.get(callingCommandId).getCodeStrPtr());
            DebuggerValueManager.addDebugger(processId, debuggerPoint);
            return debuggerPoint;
        }
        return NoDebugPoint.INSTANCE;
    }
}

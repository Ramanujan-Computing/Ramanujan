package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.ProcessorManager;
import in.ramanujan.rule.engine.checkpointing.CheckpointPusher;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;


import java.util.List;
import java.util.Map;

public class CommandManager {

    public static Long arraymappingTime = 0L;
    public static Long varCommandExec = 0L;

    public static Object process(Map<String, RuleEngineInputUnit> ruleEngineInputUnitMap, CommandRE command,
                                 String processId, ContextStack contextStack, Checkpoint checkpoint, Boolean toBeDebugged,
                                 DebuggerPoint debuggerPointContributed, CheckpointPusher checkpointPusher) {
        if(command == null) {
            return  null;
        }
        Object result = null;

        if(command.getCommandFunction() != null) {
            result = command.getCommandFunction().operate(processId, ruleEngineInputUnitMap, debuggerPointContributed,
                    contextStack, checkpoint, checkpointPusher, toBeDebugged);
        }

        if(command.getNextCommand() != null) {
            result = command.getNextCommand();
            if(ProcessorManager.isProcessDisabled(processId)) {
                result = null;
            }
            //process(mapBetweenIdAndRuleInput, (Command) mapBetweenIdAndRuleInput.get(command.getNextId()),
            //processId, contextStack, checkpoint);
        }
        return  result;
    }

    public static void updateDataAndPushCheckpoint(CommandRE command, String processId, ContextStack contextStack,
                                                   Checkpoint checkpoint, CheckpointPusher checkpointPusher) {
//        checkpoint.addStack(command.getId(), copy(contextStack));
//        checkpoint.setData(ResultManager.getResultMapForCheckpoint(processId));
//        if(checkpointPusher != null) {
//            checkpointPusher.checkAndTrigger();
//        }
    }

    private static ContextStack copy(ContextStack contextStack) {
        ContextStack contextStack1 = new ContextStack();
        List<Map<String, String>> list = contextStack.getFunctionVariableMapping();
        for(Map<String, String> map : list) {
            contextStack1.push(map);
        }
        return contextStack1;
    }

    public static void updateDataAndPopCheckpoint(String processId, Checkpoint checkpoint) {
//        checkpoint.setData(ResultManager.getResultMapForCheckpoint(processId));
//        checkpoint.pop();
    }
}

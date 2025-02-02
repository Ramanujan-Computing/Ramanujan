package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.checkpointing.CheckpointPusher;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.debugger.DebuggerValueManager;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableValue;

import java.util.Map;

public class FunctionCallManager {
    public static long totalTime = 0l;
    public static Object process(Map<String, RuleEngineInputUnit> ruleEngineInputUnitMap, FunctionCommandRE functionCommandRE,
                                 String processId, ContextStack contextStack, Checkpoint checkpoint, Boolean toBeDebugged,
                                 String callingCommandId, CheckpointPusher checkpointPusher) {
        DebuggerPoint debuggerPoint = NoDebugPoint.INSTANCE;
//        long start = System.currentTimeMillis();
        if(toBeDebugged) {
            debuggerPoint = new DebuggerPoint();
            debuggerPoint.setCommandId(callingCommandId);
            debuggerPoint.setLine(ruleEngineInputUnitMap.get(callingCommandId).getCodeStrPtr());
            DebuggerValueManager.addDebugger(processId, debuggerPoint);
        }

        final AbstractDataContainer[] functionCallExecArguments = functionCommandRE.getFunctionCallRE().getArguments();
        final AbstractDataContainer[] functionCallStackArguments = functionCommandRE.getArguments();
        int mapFromLen = functionCallExecArguments.length;

//        Map<String, String> variableMapForThisStackElement = new HashMap<>();
//
//        Map<String, String> variableMapForPreviousStackElement = contextStack.getFunctionMapping();
//        if(variableMapForPreviousStackElement != null) {
//            variableMapForThisStackElement.putAll(variableMapForPreviousStackElement);
//        }
//
//
//
//        for(int i=0; i< mapFromLen; i++) {
//            variableMapForThisStackElement.put(functionCallExecArguments.get(i).getId(), functionCallStackArguments.get(i).getId());
//        }
//        contextStack.push(variableMapForThisStackElement);


        for(int i=0; i< mapFromLen; i++) {
            Object functionCallArgument = functionCallExecArguments[i];
            Object argument = functionCallStackArguments[i];
            if(argument.getClass() == ArrayRE.class) {
                debuggerPoint.addFunctionArgCallMap(((ArrayRE) argument).getName(), ((ArrayRE)functionCallArgument).getName());
                ((ArrayRE) functionCallArgument).copyArray((ArrayRE) argument, processId);
//                ((Array) functionCallArgument).getValues().putAll(((Array) argument).getValues());
                continue;
            }
            if (argument.getClass() == VariableRE.class) {
                debuggerPoint.addFunctionArgCallMap(((VariableRE) argument).getName(), ((VariableRE) functionCallArgument).getName());
            }

            setMethodArgVariable((VariableRE) functionCallArgument, argument);
//            new AssignImpl().process(
//                    functionCallArgument,
//                    argument,
//                    processId,
//                    contextStack,
//                    ruleEngineInputUnitMap, debuggerPoint // TODO: COME BACK TO FIX FOR FUNCTIONS.
//            );
        }

//        totalTime += (System.currentTimeMillis() - start);

        CommandRE command = functionCommandRE.getFunctionCallRE().getCommand();
        while(command != null) {
            CommandManager.process(ruleEngineInputUnitMap, command, processId, contextStack, checkpoint, toBeDebugged,
                    NoDebugPoint.INSTANCE, checkpointPusher);
            command = command.getNextCommand();
        }
//        contextStack.pop();
        return null;
    }

    private static void setMethodArgVariable(VariableRE functionCallArgument, Object argument) {
        if(argument instanceof ConstantRE) {
            functionCallArgument.setValue(new VariableValue(((ConstantRE) argument).getValue(), functionCallArgument.getId()));
            return;
        }
        VariableValue value = ((VariableRE) argument).getValue();
//        value.addConnectedVariable(functionCallArgument.getId());
        functionCallArgument.setValue(((VariableRE) argument).getValue());
    }
}

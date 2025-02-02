package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.debugger.DebuggerValueManager;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.factories.OperatorTypeFactory;
import in.ramanujan.rule.engine.functioning.CommandFunction;
import in.ramanujan.rule.engine.functioning.OperatorFunctioning;
import in.ramanujan.rule.engine.manager.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableRE;
import in.ramanujan.utils.ArrayUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static in.ramanujan.rule.engine.manager.CommandManager.updateDataAndPopCheckpoint;
import static in.ramanujan.rule.engine.manager.CommandManager.updateDataAndPushCheckpoint;

@Data
public class CommandRE extends RuleEngineInputUnit {
    private CommandRE nextCommand;
    private IfRE ifRE;
    private OperationRE operationRE;
    private ConstantRE constantRE;
    private VariableRE variableRE;
    private ConditionRE conditionRE;
    private WhileRE whileRE;
    private FunctionCommandRE functionCommandRE;
    private ArrayCommandRE arrayCommandRE;
    private CommandFunction commandFunction;

    private final static OperatorFunctioning operatorFunctioning = OperatorTypeFactory.getOperatorFucntioningImpl(null);

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        Command command = (Command) ruleEngineInputUnitsBlock;
        id = command.getId();
        nextCommand = (CommandRE) map.get(command.getNextId());
        ifRE = (IfRE) map.get(command.getIfBlocks());
        operationRE = (OperationRE) map.get(command.getOperation());
        constantRE = (ConstantRE) map.get(command.getConstant());
        variableRE = (VariableRE) map.get(command.getVariableId());
        conditionRE = (ConditionRE) map.get(command.getConditionId());
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
        whileRE = (WhileRE) map.get(command.getWhileId());
        if(command.getFunctionCall() != null) {
            functionCommandRE = new FunctionCommandRE();
            functionCommandRE.setFields(command.getFunctionCall(), map);
        }
        if(command.getArrayCommand() != null) {
            arrayCommandRE = new ArrayCommandRE(command.getArrayCommand(), map);
        }

        if(constantRE != null) {
            commandFunction = (processId, mapBetweenIdAndRuleInput, debuggerPointContributed, contextStack, checkpoint, checkpointPusher, toBeDebuuged) -> {
                return constantRE;
            };
        }
        if(ifRE != null) {
            commandFunction = (CommandFunction) (processId, mapBetweenIdAndRuleInput, debuggerPointContributed,
                                                 contextStack, checkpoint, checkpointPusher, toBeDebugged) -> {
                updateDataAndPushCheckpoint(this, processId, contextStack, checkpoint, checkpointPusher);
                try {
                    IfManager.process(mapBetweenIdAndRuleInput, ifRE, processId, contextStack, checkpoint, toBeDebugged, id, checkpointPusher);
                } finally {
                    updateDataAndPopCheckpoint(processId, checkpoint);
                }
                return null;
            };
        }
        if(operationRE != null) {
            commandFunction = (CommandFunction) (processId, mapBetweenIdAndRuleInput, debuggerPointContributed,
                                                 contextStack, checkpoint, checkpointPusher, toBeDebugged) -> {
                updateDataAndPushCheckpoint(this, processId, contextStack, checkpoint, checkpointPusher);
                DebuggerPoint debuggerPoint;
                final OperationManager.EquationBasedDataOperation result;
                try {
                    if (toBeDebugged) {
                        debuggerPoint = new DebuggerPoint();
                        debuggerPoint.setCommandId(command.getId());
                        debuggerPoint.setLine(command.getCodeStrPtr());
                        result = OperationManager.process(mapBetweenIdAndRuleInput, operationRE, processId, contextStack,
                                debuggerPoint);
                        DebuggerValueManager.addDebugger(processId, debuggerPoint);
                    } else {
                        result = OperationManager.process(mapBetweenIdAndRuleInput, operationRE, processId, contextStack,
                                NoDebugPoint.INSTANCE);
                    }
                    result.get(); // this will trigger the equation.
                    return result;
                } finally {
                    updateDataAndPopCheckpoint(processId, checkpoint);
                }
            };
        }
        if(conditionRE != null) {
            commandFunction = (CommandFunction) (processId, mapBetweenIdAndRuleInput, debuggerPointContributed,
                                                 contextStack, checkpoint, checkpointPusher, toBeDebugged) -> {
                return ConditionManager.process(mapBetweenIdAndRuleInput, conditionRE,
                        processId, contextStack, debuggerPointContributed);
            };
        }
        if(functionCommandRE != null) {
            commandFunction = (CommandFunction) (processId, mapBetweenIdAndRuleInput, debuggerPointContributed,
                                                 contextStack, checkpoint, checkpointPusher, toBeDebugged) -> {
                updateDataAndPushCheckpoint(this, processId, contextStack, checkpoint, checkpointPusher);
                /**
                 * command.functionCommandRE contains the FunctionCallRE object which contains the starting command of the method called
                 * and the arguments relative to the method.
                 * command.functionCommandRE contains the list of arguments that calling member is going to call with.
                 * */
                try {
                    return FunctionCallManager.process(mapBetweenIdAndRuleInput, functionCommandRE, processId,
                            contextStack, checkpoint, toBeDebugged, id, checkpointPusher);
                } finally {
                    updateDataAndPopCheckpoint(processId, checkpoint);
                }
            };
        }
        if(whileRE != null) {
            commandFunction = (CommandFunction) (processId, mapBetweenIdAndRuleInput, debuggerPointContributed,
                                                 contextStack, checkpoint, checkpointPusher, toBeDebugged) -> {
                updateDataAndPushCheckpoint(this, processId, contextStack, checkpoint, checkpointPusher);
                try {
                    WhileManager.process(mapBetweenIdAndRuleInput, whileRE, processId, contextStack, checkpoint,
                            toBeDebugged, command.getId(), checkpointPusher);
                } finally {
                    updateDataAndPopCheckpoint(processId, checkpoint);
                }
                return null;
            };
        }
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new CommandRE();
    }
}

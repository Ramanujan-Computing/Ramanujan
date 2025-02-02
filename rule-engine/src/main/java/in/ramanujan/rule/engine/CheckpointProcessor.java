package in.ramanujan.rule.engine;

import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.FunctionStackAtInstant;
import in.ramanujan.rule.engine.checkpointing.ICheckpointPushClient;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.manager.*;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayValue;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableValue;
import in.ramanujan.utils.Constants;

import java.util.HashMap;
import java.util.Map;

public class CheckpointProcessor extends Processor {
    public CheckpointProcessor(RuleEngineInput ruleEngineInput, Checkpoint checkpoint, ICheckpointPushClient checkpointPushClient) {
        super(ruleEngineInput, null, checkpointPushClient);
        setCheckpoint(checkpoint);
    }

    @Override
    public Map<String, Object> process() {
        FunctionStackAtInstant functionStackAtInstant = getCheckpoint().pop();
        String processId = getProcessId();
        initCheckpointVariables((Map<String, Object>) getCheckpoint().getData(), getMapBetweenIdAndRuleInput());
        ResultManager.initVariableMap(processId, (Map<String, Object>) getCheckpoint().getData());
        if(functionStackAtInstant != null) {
            ContextStack contextStack = functionStackAtInstant.getContextStack();
            for(Map<String, String> stackElementMap : contextStack.getFunctionVariableMapping()) {
                for(Map.Entry<String, String> entry : stackElementMap.entrySet()) {
                    String mappedTo = entry.getKey();
                    String mappedFrom = entry.getValue();

                    RuleEngineInputUnit mappedToObj = getMapBetweenIdAndRuleInput().get(mappedTo);
                    RuleEngineInputUnit mappedFromObj = getMapBetweenIdAndRuleInput().get(mappedFrom);

                    if(!(mappedFromObj instanceof ConstantRE)) {
                        if(mappedFromObj instanceof ArrayRE) {
                            ArrayValue arrayValue = ((ArrayRE) mappedFromObj).getValues();
                            ((ArrayRE) mappedToObj).setValues(arrayValue);
                            arrayValue.addConnectedArray(mappedToObj.getId());
                        } else if (mappedFromObj instanceof  VariableRE) {
                            VariableValue variableValue = ((VariableRE) mappedFromObj).getValue();
                            ((VariableRE) mappedToObj).setValue(variableValue);
                            variableValue.addConnectedVariable(mappedToObj.getId());
                        }
                    }
                }
            }
        }
        while(functionStackAtInstant != null) {
            ContextStack contextStack = functionStackAtInstant.getContextStack();
            Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput = getMapBetweenIdAndRuleInput();
            RuleEngineInputUnit ruleEngineInputUnits = mapBetweenIdAndRuleInput.get(functionStackAtInstant.getRuleEngineUnitId());
            if(ruleEngineInputUnits.getClass() == CommandRE.class) {
                CommandRE command = (CommandRE) ruleEngineInputUnits;
                while(command != null) {
                    Object result = CommandManager.process(mapBetweenIdAndRuleInput, command, processId, contextStack, getCheckpoint(), getToBeDebugged(), NoDebugPoint.INSTANCE, checkpointPusher);
                    command = command.getNextCommand();
                }
            }

            functionStackAtInstant = getCheckpoint().pop();
            CommandRE nextCommandRE = getNextId(functionStackAtInstant);
            while (nextCommandRE == null && functionStackAtInstant != null) {
                functionStackAtInstant = getCheckpoint().pop();
                nextCommandRE = getNextId(functionStackAtInstant);
            }

            if(functionStackAtInstant != null) {
                functionStackAtInstant.setRuleEngineUnitId(nextCommandRE.getId());
            }

        }
        return ResultManager.getResultMap(processId);
    }

    private void initCheckpointVariables(Map<String, Object> mapFromCheckpoint,
                                         Map<String, RuleEngineInputUnit> ruleEngineInputUnitMap) {
        if(mapFromCheckpoint == null) {
            return;
        }
        Map<String, Object> variableMap  = new HashMap<>();
        for(String key : mapFromCheckpoint.keySet()) {
            if(Constants.arrayIndex.equalsIgnoreCase(key)) {
                Map<String, Map<String, Object>> arrayIndexMap = (Map<String, Map<String, Object>>)
                        mapFromCheckpoint.get(key);
                if(arrayIndexMap != null) {
                    for(String arrayId : arrayIndexMap.keySet()) {
                        ArrayRE arrayRE = (ArrayRE) ruleEngineInputUnitMap.get(arrayId);
                        Map<String, Object> keyValMap = arrayIndexMap.get(arrayId);
                        if(keyValMap != null) {
                            for(String keyStr : keyValMap.keySet()) {
                                arrayRE.getValues().setVal(keyStr, (double) keyValMap.get(keyStr), new int[0]);
                            }
                        }
                    }
                }
//                ArrayManager.populateArrayMap(processId, (Map<String, Map<String, Object>>)mapFromCheckpoint.get(Constants.arrayIndex));
            } else {
                final VariableRE variableRE = ((VariableRE)ruleEngineInputUnitMap.get(key));
                variableRE.setValue(new VariableValue((double) mapFromCheckpoint.get(key), variableRE.getId()));
            }
        }
    }


    private CommandRE getNextId(FunctionStackAtInstant functionStackAtInstant) {
        if (functionStackAtInstant == null) {
            return null;
        }
        CommandRE commandForFunctionStackAtInstant = (CommandRE) getMapBetweenIdAndRuleInput()
                .get(functionStackAtInstant.getRuleEngineUnitId());
        if(commandForFunctionStackAtInstant.getOperationRE() != null || commandForFunctionStackAtInstant.getWhileRE() != null) {
            return commandForFunctionStackAtInstant;
        }
        return commandForFunctionStackAtInstant.getNextCommand();
    }
}


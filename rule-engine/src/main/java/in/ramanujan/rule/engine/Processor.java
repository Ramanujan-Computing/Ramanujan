package in.ramanujan.rule.engine;

import in.ramanujan.debugger.UserReadableDebugPoint;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.checkpointing.CheckpointPusher;
import in.ramanujan.rule.engine.checkpointing.ICheckpointPushClient;
import in.ramanujan.rule.engine.debugger.DebuggerValueManager;
import in.ramanujan.rule.engine.debugger.IDebugPushClient;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.functioning.operatorFunctioningImpl.AssignImpl;
import in.ramanujan.rule.engine.manager.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.VariableValue;

import java.util.*;


public class Processor {
    private Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput;
    private String firstCommandId;
    private Object resultOfProcessing;
    private String processId;
    private RuleEngineInput ruleEngineInput;
    private Checkpoint checkpoint;
    private Boolean isStopped;

    private Boolean toBeDebugged = false;

    protected final ICheckpointPushClient checkpointPushClient;
    protected CheckpointPusher checkpointPusher;

    public Processor(RuleEngineInput ruleEngineInput, String firstCommandId, ICheckpointPushClient checkpointPushClient) {

        processId = UUID.randomUUID().toString();
        this.ruleEngineInput = ruleEngineInput;
        mapBetweenIdAndRuleInput = createMap(ruleEngineInput);
        populateFieldsInRuleEngineUnitObjects(mapBetweenIdAndRuleInput, ruleEngineInput);
        setVariableManager();
        this.firstCommandId = firstCommandId;
        isStopped = false;
        checkpoint = new Checkpoint();
        this.checkpointPushClient = checkpointPushClient;
        checkpointPusher = new CheckpointPusher(checkpoint, this.checkpointPushClient);
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    protected void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
        checkpointPusher = new CheckpointPusher(checkpoint, checkpointPushClient);
    }

    public void setToBeDebugged(final Boolean toBeDebugged, IDebugPushClient iDebugPushClient, Set<Integer> debugLineHooksSet) {
        this.toBeDebugged = toBeDebugged;
        DebuggerValueManager.addDebugPushClient(processId, iDebugPushClient);
        DebuggerValueManager.addProcessRuleEngineUnitMap(processId, mapBetweenIdAndRuleInput);
    }

    public Boolean getToBeDebugged() {
        return toBeDebugged;
    }

    protected Map<String, RuleEngineInputUnit> getMapBetweenIdAndRuleInput() {
        return mapBetweenIdAndRuleInput;
    }

    protected String getProcessId() {
        return processId;
    }

    public Map<String, Object> process() {
        CommandRE command = (CommandRE) mapBetweenIdAndRuleInput.get(firstCommandId);
        while(command != null) {
            CommandManager.process(mapBetweenIdAndRuleInput, command, processId, new ContextStack(), checkpoint, toBeDebugged, NoDebugPoint.INSTANCE, checkpointPusher);
            command = command.getNextCommand();
        }
        return ResultManager.getResultMap(processId);
    }

    /**
     * Calling app will call this method after it catches {@link in.ramanujan.rule.engine.debugger.DebugLineHooked}
     */
    public List<UserReadableDebugPoint> getDebugPoints() throws Exception {
        if(!toBeDebugged) {
            return null;
        }
        return DebuggerValueManager.getDebugPoints(processId, mapBetweenIdAndRuleInput);
    }


    public void endProcess() {
        VariableManager.removeProcess(processId);
        ArrayManager.removeProcess(processId);
        ProcessorManager.disableProcess(processId);
        System.out.println("Total time in equal " + AssignImpl.time);
        System.out.println("Total time in arrayMapping" + ArrayManager.time);
        System.out.println("Total time in functionMap" + FunctionCallManager.totalTime);
        ArrayManager.time = 0l;
        AssignImpl.time = 0l;
        FunctionCallManager.totalTime = 0l;
    }

    private void setVariableManager() {
        Map<String, Object> variableMap = new HashMap<>();
        for(Variable variable : ruleEngineInput.getVariables()) {
            if(variable.getValue() != null) {
                //VariableManager.process(variable, processId, true, false, variable.getValue(), new ContextStack());
                variableMap.put(variable.getId(), variable.getValue());
            }
        }
//        VariableManager.populateVariableMap(processId, variableMap);
        Map<String, Map<String, Object>> arrayMap = new HashMap<>();
        for(Array array : ruleEngineInput.getArrays()) {
            if(array.getValues() == null) {
                arrayMap.put(array.getId(), new HashMap<>());
            } else {
                arrayMap.put(array.getId(), array.getValues());
            }
//            ContextStack contextStack = new ContextStack();
//            Map<String, Object> values = array.getValues();
//            if(values ==  null) {
//                values = new HashMap<>();
//            }
//            for(String index : values.keySet()) {
//                ArrayManager.process(array, index, processId, true, false, values.get(index), contextStack, mapBetweenIdAndRuleInput);
//            }
        }
//        ArrayManager.populateArrayMap(processId, arrayMap);
    }

    private void storeInIdMap(Map<String, RuleEngineInputUnit> idMap, List<RuleEngineInputUnits> obj, RuleEngineInputUnit sampleRuleInputUnit) {
        if (obj == null) {
            return;
        }
        for (RuleEngineInputUnits ruleEngineInputUnit : obj) {
            idMap.put(ruleEngineInputUnit.getId(), sampleRuleInputUnit.createNewObject());
        }

    }

    private Map<String, RuleEngineInputUnit> createMap(RuleEngineInput ruleEngineInputUnits) {
        Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput = new HashMap<String, RuleEngineInputUnit>();
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getCommands(), new CommandRE());

        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getVariables(), new VariableRE());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getIfBlocks(), new IfRE());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getOperations(), new OperationRE());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getConditions(), new ConditionRE());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getConstants(), new ConstantRE());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getArrays(), new ArrayRE());
        storeInIdMap(mapBetweenIdAndRuleInput,  (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getFunctionCalls(), new FunctionCallRE());
        storeInIdMap(mapBetweenIdAndRuleInput,  (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getWhileBlocks(), new WhileRE());
        return mapBetweenIdAndRuleInput;
    }

    private void populateFieldsInRuleEngineUnitObjects(Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, RuleEngineInput ruleEngineInputUnit) {
        populateField(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getCommands());

        populateField(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getVariables());
        populateField(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getIfBlocks());
        populateField(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getOperations());
        populateField(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getConditions());
        populateField(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getConstants());
        populateField(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getArrays());
        populateField(mapBetweenIdAndRuleInput,  (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getFunctionCalls());
        populateField(mapBetweenIdAndRuleInput,  (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnit.getWhileBlocks());
    }

    private void populateField(Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, List<RuleEngineInputUnits> ruleEngineInputUnitsList) {
        if(ruleEngineInputUnitsList == null) {
            return;
        }
        for(RuleEngineInputUnits ruleEngineInputUnits : ruleEngineInputUnitsList) {
            mapBetweenIdAndRuleInput.get(ruleEngineInputUnits.getId()).setFields(ruleEngineInputUnits, mapBetweenIdAndRuleInput);
        }
    }
}

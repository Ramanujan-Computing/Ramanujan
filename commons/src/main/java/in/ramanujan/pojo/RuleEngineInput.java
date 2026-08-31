package in.ramanujan.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleEngineInput {
    private List<Variable> variables;
    private List<Command> commands;
    private List<If> ifBlocks;
    private List<Operation> operations;
    private List<Condition> conditions;
    private List<Constant> constants;
    private List<Array> arrays;
    private List<MethodDataTypeAgnosticArg> methodDataTypeAgnosticArgs;
    private List<FunctionCall> functionCalls;
    private List<While> whileBlocks;
    private List<RedefineArrayCommand> redefineArrayCommands = new ArrayList<>();
    private List<ReturnOperation> returnOperations;

    public RuleEngineInput() {
        variables = new ArrayList<>();
        commands = new ArrayList<>();
        ifBlocks = new ArrayList<>();
        operations = new ArrayList<>();
        conditions = new ArrayList<>();
        constants = new ArrayList<>();
        arrays = new ArrayList<>();
        whileBlocks = new ArrayList<>();
        methodDataTypeAgnosticArgs = new ArrayList<>();
        functionCalls = new ArrayList<>();
        returnOperations = new ArrayList<>();
    }

    public List<RedefineArrayCommand> getRedefineArrayCommands() {
        return redefineArrayCommands;
    }
    public void setRedefineArrayCommands(List<RedefineArrayCommand> redefineArrayCommands) {
        this.redefineArrayCommands = redefineArrayCommands;
    }

    public void addAllPartsOfGivenRuleEngineInput(RuleEngineInput ruleEngineInput) {
        // Deduplicate variables and arrays by object identity — child thread DAG elements
        // may have already had some globals added via getExistingVariable/getExistingArray,
        // so we must not re-add the same object reference (duplicate IDs crash the native lib).
        java.util.Set<Variable> existingVars = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        existingVars.addAll(variables);
        for (Variable v : ruleEngineInput.getVariables()) {
            if (existingVars.add(v)) variables.add(v);
        }

        java.util.Set<Array> existingArrays = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        existingArrays.addAll(arrays);
        for (Array a : ruleEngineInput.getArrays()) {
            if (existingArrays.add(a)) arrays.add(a);
        }

        commands.addAll(ruleEngineInput.getCommands());
        ifBlocks.addAll(ruleEngineInput.getIfBlocks());
        operations.addAll(ruleEngineInput.getOperations());
        conditions.addAll(ruleEngineInput.getConditions());
        constants.addAll(ruleEngineInput.getConstants());
        functionCalls.addAll(ruleEngineInput.getFunctionCalls());
        whileBlocks.addAll(ruleEngineInput.getWhileBlocks());
        redefineArrayCommands.addAll(ruleEngineInput.getRedefineArrayCommands());
        methodDataTypeAgnosticArgs.addAll(ruleEngineInput.getMethodDataTypeAgnosticArgs());
        returnOperations.addAll(ruleEngineInput.getReturnOperations());
    }

}

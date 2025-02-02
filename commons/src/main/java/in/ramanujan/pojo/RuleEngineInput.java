package in.ramanujan.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
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
    private List<FunctionCall> functionCalls;
    private List<While> whileBlocks;

    public RuleEngineInput() {
        variables = new ArrayList<>();
        commands = new ArrayList<>();
        ifBlocks = new ArrayList<>();
        operations = new ArrayList<>();
        conditions = new ArrayList<>();
        constants = new ArrayList<>();
        arrays = new ArrayList<>();
        whileBlocks = new ArrayList<>();
        functionCalls = new ArrayList<>();
    }

    public void addAllPartsOfGivenRuleEngineInput(RuleEngineInput ruleEngineInput) {
        variables.addAll(ruleEngineInput.getVariables());
        commands.addAll(ruleEngineInput.getCommands());
        ifBlocks.addAll(ruleEngineInput.getIfBlocks());
        operations.addAll(ruleEngineInput.getOperations());
        conditions.addAll(ruleEngineInput.getConditions());
        constants.addAll(ruleEngineInput.getConstants());
        functionCalls.addAll(ruleEngineInput.getFunctionCalls());
        arrays.addAll(ruleEngineInput.getArrays());
        whileBlocks.addAll(ruleEngineInput.getWhileBlocks());
    }

}

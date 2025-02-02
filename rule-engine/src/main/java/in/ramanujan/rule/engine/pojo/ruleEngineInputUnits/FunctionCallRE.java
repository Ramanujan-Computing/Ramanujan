package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class FunctionCallRE extends RuleEngineInputUnit{

    private AbstractDataContainer[] arguments;
    private CommandRE command;

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        FunctionCall functionCall = (FunctionCall) ruleEngineInputUnitsBlock;
        id = functionCall.getId();
        if(functionCall.getArguments() != null) {
            arguments = new AbstractDataContainer[functionCall.getArguments().size()];
            int counter = 0;
            for(String argumentId : functionCall.getArguments()) {
                arguments[counter++] = (AbstractDataContainer) map.get(argumentId);
            }
        }
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
        command = (CommandRE) map.get(functionCall.getFirstCommandId());
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new FunctionCallRE();
    }
}

package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class FunctionCommandRE extends RuleEngineInputUnit {
    private FunctionCallRE functionCallRE;
    private AbstractDataContainer[] arguments;

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        FunctionCall functionCall = (FunctionCall) ruleEngineInputUnitsBlock;
        id = functionCall.getId();
        functionCallRE = (FunctionCallRE) map.get(functionCall.getId());
        if(functionCall.getArguments() != null) {
            arguments = new AbstractDataContainer[functionCall.getArguments().size()];
            int counter = 0;
            for(String argStr : functionCall.getArguments()) {
                arguments[counter++] = (AbstractDataContainer) map.get(argStr);
            }
        }
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new FunctionCommandRE();
    }
}

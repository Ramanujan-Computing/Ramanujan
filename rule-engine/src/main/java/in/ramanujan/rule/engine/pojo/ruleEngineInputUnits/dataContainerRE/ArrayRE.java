package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.manager.ArrayManager;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.AbstractDataContainer;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataContainerRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ArrayRE extends RuleEngineInputUnit implements AbstractDataContainer {
    private String name, dataType;
    private ArrayValue values;


    public void copyArray(ArrayRE referenceArray, String processId) {
        this.values = referenceArray.getValues();
//        referenceArray.getValues().addConnectedArray(this.getId());
//        values.putAll(referenceArray.getValues());//This is commented so arrayValue object can be used.



//        for(String index : values.keySet()) {
//            ArrayManager.updateChangeLog(processId, this.getId(), index, values.get(index));
//        }
    }

    public ArrayValDataContainer getVal(int[] indexes) {
        return getValues().getVal(indexes);
    }

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        id = ruleEngineInputUnitsBlock.getId();
        name = ((Array)ruleEngineInputUnitsBlock).getName();
        dataType = ((Array) ruleEngineInputUnitsBlock).getDataType();
        values = new ArrayValue((Array) ruleEngineInputUnitsBlock, id);
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new ArrayRE();
    }
}

package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayCommand;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayRE;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class ArrayCommandRE {
    @Getter
    private ArrayRE arrayRE;
    private List<String> index;
    private List<DataContainerRE> dataContainerREList;
    @Getter
    private int size;


    public ArrayCommandRE(ArrayCommand arrayCommand, Map<String, RuleEngineInputUnit> map) {
        arrayRE = (ArrayRE) map.get(arrayCommand.getArrayId());
        index = arrayCommand.getIndex();
        dataContainerREList = new ArrayList<>();
        size = index.size();
    }

    /**
     * This method should be called only serially. like, for loop on the index field and call this method.
     */
    public DataContainerRE getIndex(int indexInt, Map<String, RuleEngineInputUnit> map) {
        if(dataContainerREList.size() <= indexInt) {
            DataContainerRE res = (DataContainerRE) map.get(index.get(indexInt));
            dataContainerREList.add(res);
            return res;
        }
        return dataContainerREList.get(indexInt);
    }

}

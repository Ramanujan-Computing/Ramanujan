package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE;

import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataOperation;

public class ArrayResolver implements DataOperation {

    private final ArrayRE arrayRE;
    private final DataOperation[] indexes;
    private int[] indexesArr;


    private ArrayValDataContainer getArrayValDataContainer() {
        int i=0;
        for(DataOperation dataOperation : this.indexes) {
            indexesArr[i++] = (((int)dataOperation.get()));
        }
        return arrayRE.getVal(indexesArr.clone());
    }

    @Override
    public double get() {
        return getArrayValDataContainer().get();
    }

    @Override
    public boolean greaterThan(double val) {
        return getArrayValDataContainer().greaterThan(val);
    }

    @Override
    public boolean greaterThanOrEqual(double val) {
        return getArrayValDataContainer().greaterThanOrEqual(val);
    }

    @Override
    public boolean isEqual(double val) {
        return getArrayValDataContainer().isEqual(val);
    }

    @Override
    public boolean isNotEqual(double val) {
        return getArrayValDataContainer().isNotEqual(val);
    }

    @Override
    public boolean lessThanOrEqual(double val) {
        return getArrayValDataContainer().lessThanOrEqual(val);
    }

    @Override
    public boolean lessThan(double val) {
        return getArrayValDataContainer().lessThan(val);
    }

    public ArrayResolver(ArrayRE arrayRE, DataOperation[] indexes) {
        this.arrayRE = arrayRE;
        this.indexes = indexes;
        this.indexesArr = new int[indexes.length];
    }

    @Override
    public double add(double value) {
        return getArrayValDataContainer().add(value);
    }

    @Override
    public double minus(double value) {
        return getArrayValDataContainer().minus(value);
    }

    @Override
    public double power(double value) {
        return getArrayValDataContainer().power(value);
    }

    @Override
    public double mul(double value) {
        return getArrayValDataContainer().mul(value);
    }

    @Override
    public double divide(double value) {
        return getArrayValDataContainer().divide(value);
    }

    @Override
    public Object set(double value, String processId) {
        ArrayValDataContainer arrayValDataContainer = getArrayValDataContainer();
        arrayRE.getValues().registerSet(processId, arrayValDataContainer);
        return arrayValDataContainer.set(value, processId);
    }
}

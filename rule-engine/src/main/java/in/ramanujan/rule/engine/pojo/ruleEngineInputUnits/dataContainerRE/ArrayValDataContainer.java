package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE;

import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataOperation;
import lombok.Getter;

import java.util.UUID;

public class ArrayValDataContainer implements DataOperation {

    private double value;

    @Getter
    private final int[] index;
    private final String uuid = UUID.randomUUID().toString();

    public ArrayValDataContainer(double val, int[] index) {
        this.value = val;
        this.index = index;
    }

    @Override
    public double add(double value) {
        return get() + value;
    }

    @Override
    public double minus(double value) {
        return get() - value;
    }

    @Override
    public double power(double value) {
        return Math.pow(get(), value);
    }

    @Override
    public double mul(double value) {
        return get() * value;
    }

    @Override
    public double divide(double value) {
        return  get() / value;
    }

    @Override
    public Object set(double value, String processId) {
        this.value = value;
        return value;
    }

    @Override
    public double get() {
        return this.value;
    }

    @Override
    public boolean greaterThan(double val) {
        return get() > val;
    }

    @Override
    public boolean greaterThanOrEqual(double val) {
        return get() >= val;
    }

    @Override
    public boolean isEqual(double val) {
        return get() == val;
    }

    @Override
    public boolean isNotEqual(double val) {
        return get() != val;
    }

    @Override
    public boolean lessThanOrEqual(double val) {
        return get() <= val;
    }

    @Override
    public boolean lessThan(double val) {
        return get() < val;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ArrayValDataContainer)) {
            return false;
        }
        return uuid.equals(((ArrayValDataContainer) obj).uuid);
    }
}

package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

import static in.ramanujan.rule.engine.functioning.OperatorFunctioning.NOT_IMPL;

public class ConstantRE extends DataContainerRE {

    @Getter
    @Setter
    private double value;

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        Constant constant = (Constant) ruleEngineInputUnitsBlock;
        id = constant.getId();
        dataType = constant.getDataType();
        value = (double) constant.getValue();
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new ConstantRE();
    }

    @Override
    public double add(double value) {
        return this.value + value;
    }

    @Override
    public double minus(double value) {
        return this.value - value;
    }

    @Override
    public double power(double value) {
        return Math.pow(this.value, value);
    }

    @Override
    public boolean greaterThan(double val) {
        return this.value > val;
    }

    @Override
    public boolean greaterThanOrEqual(double val) {
        return this.value >= val;
    }

    @Override
    public boolean isEqual(double val) {
        return this.value == val;
    }

    @Override
    public boolean isNotEqual(double val) {
        return this.value != val;
    }

    @Override
    public boolean lessThanOrEqual(double val) {
        return this.value <= val;
    }

    @Override
    public boolean lessThan(double val) {
        return this.value < val;
    }

    @Override
    public double mul(double value) {
        return this.value * value;
    }

    @Override
    public double divide(double value) {
        return this.value/value;
    }

    @Override
    public Object set(double value, String processId) {
        throw new RuntimeException(NOT_IMPL);
    }

    @Override
    public double get() {
        return this.value;
    }
}

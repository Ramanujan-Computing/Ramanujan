package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.rule.engine.manager.VariableManager;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataContainerRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class VariableRE extends DataContainerRE {
    private VariableValue value;

    private boolean registerValChange;

    @Override
    public void setFields(RuleEngineInputUnits ruleEngineInputUnitsBlock, Map<String, RuleEngineInputUnit> map) {
        id = ruleEngineInputUnitsBlock.getId();
        name = ((Variable) ruleEngineInputUnitsBlock).getName();
        dataType = ((Variable) ruleEngineInputUnitsBlock).getDataType();
        value = new VariableValue((Double) ((Variable) ruleEngineInputUnitsBlock).getValue(), id);
        codeStrPtr = ruleEngineInputUnitsBlock.getCodeStrPtr();
    }

    public VariableValue getValue() {
        return value;
    }

    @Override
    public double add(double value) {
        return getValue().val + value;
    }

    @Override
    public double minus(double value) {
        return getValue().val - value;
    }

    @Override
    public double power(double value) {
        return Math.pow(getValue().val, value);
    }

    @Override
    public double mul(double value) {
        return getValue().val * value;
    }

    @Override
    public double divide(double value) {
        return  getValue().val / value;
    }

    @Override
    public Object set(double value, String processId) {
        getValue().val = value;
        if(!registerValChange) {
            VariableManager.updateChangeLog(processId, getValue());
            registerValChange = true;
        }
        return getValue().val;
    }

    @Override
    public double get() {
        return getValue().val;
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

    public void setValue(VariableValue value) {
        this.value = value;
    }

    @Override
    public RuleEngineInputUnit createNewObject() {
        return new VariableRE();
    }
}

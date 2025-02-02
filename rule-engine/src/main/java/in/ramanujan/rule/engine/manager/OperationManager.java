package in.ramanujan.rule.engine.manager;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.operatorFunctioningImpl.CachedOperationFunctioning;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.DataOperation;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.OperationRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

import static in.ramanujan.rule.engine.functioning.OperatorFunctioning.NOT_IMPL;

public class OperationManager {
    public static Long equalTime = 0L;
    public static Long equalOpTime = 0L;
    public static EquationBasedDataOperation process(Map<String, RuleEngineInputUnit> ruleEngineInputUnitMap, OperationRE operationRE,
                                 String processId, ContextStack contextStack,
                                 final DebuggerPoint debuggerPoint) {
        CachedOperationFunctioning cachedOperationFunctioning = operationRE.getCachedOperationFunctioning();
        if(cachedOperationFunctioning == null) {
            cachedOperationFunctioning =  operationRE.getOperatorFunctioning().process(operationRE.getOperand1(),
                    operationRE.getOperand2(), processId, contextStack, ruleEngineInputUnitMap, debuggerPoint);
            operationRE.setCachedOperationFunctioning(cachedOperationFunctioning);
        }
        return new EquationBasedDataOperation(cachedOperationFunctioning);
    }

    public static class EquationBasedDataOperation implements DataOperation {
        private final CachedOperationFunctioning val;

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
        public double mul(double value) {
            return get() * value;
        }

        @Override
        public double divide(double value) {
            return get()/value;
        }

        @Override
        public Object set(double value, String processId) {
            throw new RuntimeException(NOT_IMPL);
        }

        @Override
        public double get() {
            return (double) val.operate();
        }

        public EquationBasedDataOperation(CachedOperationFunctioning val) {
            this.val = val;
        }
    }
}

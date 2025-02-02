package in.ramanujan.rule.engine.factories;

import in.ramanujan.enums.ConditionType;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.ConditionFunctioning;
import in.ramanujan.rule.engine.functioning.condtionFunctioningImpl.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;


public class ConditionTypeFactory {
    public static  ConditionFunctioning getConditionFunctioningImpl(ConditionType conditionType) {
        if(conditionType == ConditionType.lessThan) {
            return new LessThanImpl();
        }
        if(conditionType == ConditionType.and) {
            return new AndImpl();
        }
        if(conditionType == ConditionType.or) {
            return new OrImpl();
        }
        if(conditionType == ConditionType.lessThanEqualTo) {
            return new LessThanEqualToImpl();
        }
        if(conditionType == ConditionType.greaterThan) {
            return new GreaterThanImpl();
        }
        if(conditionType == ConditionType.greaterThanEqualTo) {
            return new GreaterThanEqualToImpl();
        }
        if(conditionType == ConditionType.isEqual) {
            return new IsEqualImpl();
        }
        if(conditionType == ConditionType.isNotEqual) {
            return new IsNotEqualImpl();
        }
        if(conditionType == ConditionType.not) {
            return new NotImpl();
        }

        return  new ConditionFunctioning() {
            @Override
            public CachedConditionFunctioning process(CommandRE compareWhat, CommandRE compareWith, String processId,
                                                      Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput,
                                                      ContextStack contextStack, DebuggerPoint debuggerPoint) {
                return() -> {return false;};
            }
        };
    }
}

package in.ramanujan.rule.engine.factories;

import in.ramanujan.enums.OperatorType;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.OperatorFunctioning;
import in.ramanujan.rule.engine.functioning.operatorFunctioningImpl.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;

import java.util.Map;

public class OperatorTypeFactory {
    public static OperatorFunctioning getOperatorFucntioningImpl(OperatorType operatorType) {
        if(operatorType == OperatorType.ADD) {
            return new AddImpl();
        }
        if(operatorType == OperatorType.ASSIGN) {
            return new AssignImpl();
        }
        if(operatorType == OperatorType.MINUS) {
            return new MinusImpl();
        }
        if(operatorType == OperatorType.MULTIPLY) {
            return new MultiplyImpl();
        }
        if(operatorType == OperatorType.DIVIDE) {
            return new DivideImpl();
        }
        if(operatorType == OperatorType.LOG) {
            return new LogImpl();
        }
        if(operatorType == OperatorType.POWER) {
            return new PowerImpl();
        }
        if(operatorType == OperatorType.SINE) {
            return new SineImpl();
        }
        if(operatorType == OperatorType.COSINE) {
            return new CosineImpl();
        }
        return new OperatorFunctioning() {
            @Override
            public CachedOperationFunctioning process(CommandRE operand1, CommandRE operand2, String processId, ContextStack contextStack, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, DebuggerPoint debuggerPoint) {
                return null;
            }
        };
    }
}

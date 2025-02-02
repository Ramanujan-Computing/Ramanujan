package in.ramanujan.rule.engine.functioning;

import in.ramanujan.pojo.ContextStack;
import in.ramanujan.rule.engine.debugger.DebuggerPoint;
import in.ramanujan.rule.engine.functioning.operatorFunctioningImpl.CachedOperationFunctioning;
import in.ramanujan.rule.engine.manager.OperationManager;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.*;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.dataContainerRE.ArrayResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface OperatorFunctioning {
    public CachedOperationFunctioning process(CommandRE operand1, CommandRE operand2, String processId, ContextStack contextStack,
                                              Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput, DebuggerPoint debuggerPoint);

    static DataOperation getDataContainerRE(CommandRE commandRE, Map<String, RuleEngineInputUnit> mapBetweenIdAndRuleInput,
                                             String processId, ContextStack contextStack, DebuggerPoint debuggerPoint) {
        if(commandRE == null) {
            return null;
        }
        if(commandRE.getArrayCommandRE() != null) {
            ArrayCommandRE arrayCommandRE = commandRE.getArrayCommandRE();
            DataOperation[] list = new DataOperation[arrayCommandRE.getSize()];
            for(int i=0; i < arrayCommandRE.getSize(); i++) {
                list[i] = (arrayCommandRE.getIndex(i, mapBetweenIdAndRuleInput));
            }
            return new ArrayResolver(arrayCommandRE.getArrayRE(), list);
        }
        if(commandRE.getVariableRE() != null) {
            return commandRE.getVariableRE();
        }
        if(commandRE.getOperationRE() != null) {
            return OperationManager.process(mapBetweenIdAndRuleInput, commandRE.getOperationRE(), processId, contextStack, debuggerPoint);
        }
        if(commandRE.getConstantRE() != null) {
            return commandRE.getConstantRE();
        }
        throw new RuntimeException(NOT_IMPL);
    }

    static final String NOT_IMPL = "Not implemented";
}

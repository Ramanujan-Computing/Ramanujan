package in.ramanujan.middleware.base.dagChecker;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuleEngineInputChecker {

    private Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2;


    public Boolean checkRuleEngineInput(RuleEngineInput ruleEngineInputDag1, RuleEngineInput ruleEngineInputDag2,
                                        String firstCommandIdDag1, String firstCommandIdDag2) {
        mapBetweenIdAndRuleInputDag2 = new HashMap<>();
        mapBetweenIdAndRuleInputDag1 = new HashMap<>();
        createMap(ruleEngineInputDag1, mapBetweenIdAndRuleInputDag1);
        createMap(ruleEngineInputDag2, mapBetweenIdAndRuleInputDag2);

        return (new ProcessChecker()).checkProcessing(mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2,
                firstCommandIdDag1, firstCommandIdDag2);
    }

    private void storeInIdMap(Map<String, RuleEngineInputUnits> idMap, List<RuleEngineInputUnits> obj) {
        if (obj == null) {
            return;
        }
        for (RuleEngineInputUnits ruleEngineInputUnit : obj) {
            idMap.put(ruleEngineInputUnit.getId(), ruleEngineInputUnit);
        }

    }

    private void createMap(RuleEngineInput ruleEngineInputUnits, Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInput) {

        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getCommands());

        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getVariables());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getIfBlocks());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getOperations());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getConditions());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getConstants());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getArrays());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getFunctionCalls());
        storeInIdMap(mapBetweenIdAndRuleInput, (List<RuleEngineInputUnits>)
                (List<? extends RuleEngineInputUnits>) ruleEngineInputUnits.getWhileBlocks());
    }
}

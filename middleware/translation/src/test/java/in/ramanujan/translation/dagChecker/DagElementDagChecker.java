package in.ramanujan.translation.dagChecker;


import in.ramanujan.translation.codeConverter.DagElement;

public class DagElementDagChecker{
    RuleEngineInputChecker ruleEngineInputChecker;

    public DagElementDagChecker() {
        ruleEngineInputChecker = new RuleEngineInputChecker();
    }

    public Boolean checkNodesIfSame(DagElement dagElement1, DagElement dagElement2) {
        if(dagElement1 == null && dagElement2 != null) {
            return false;
        }
        if(dagElement1 != null && dagElement2 == null) {
            return  false;
        }
        if(dagElement1 == null && dagElement2 == null) {
            return  true;
        }
        if(ruleEngineInputChecker.checkRuleEngineInput(dagElement1.getRuleEngineInput(), dagElement2.getRuleEngineInput(),
                dagElement1.getFirstCommandId(), dagElement2.getFirstCommandId())) {
            for(DagElement dagElementNextDag1 : dagElement1.getNextElements()) {
                boolean flag = false;
                for(DagElement dagElementNextDag2 : dagElement2.getNextElements()) {
                    if(checkNodesIfSame(dagElementNextDag1, dagElementNextDag2)) {
                        flag = true;
                        break;
                    }
                }
                if(!flag) {
                    return  false;
                }
            }
            return true;
        } else {
            return false;
        }
        //return null;
    }
}

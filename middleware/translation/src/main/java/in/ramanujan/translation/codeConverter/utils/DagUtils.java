package in.ramanujan.translation.codeConverter.utils;



import in.ramanujan.translation.codeConverter.DagElement;

import java.util.*;

public class DagUtils {
    public static DagElement getFirstElementOfDag(DagElement lastElement, List<DagElement> dagElementListToBePopulated) {
        dagElementListToBePopulated.add(lastElement);
        List<DagElement>  levelComputeUnits = Collections.singletonList(lastElement);
        Set<String> seenDagElements = new HashSet<>();
        Boolean loopSwitchedOn = true;
        while(loopSwitchedOn) {
            List<DagElement> nextLevelComputeUnits = new ArrayList<>();
            for(DagElement dagElement : levelComputeUnits) {
                if(dagElement.getPreviousElements() == null || dagElement.getPreviousElements().size() == 0) {
                    return dagElement;
                }
                for(DagElement previousElement : dagElement.getPreviousElements()) {
                    if(!seenDagElements.contains(previousElement.getId())) {
                        dagElementListToBePopulated.add(previousElement);
                        seenDagElements.add(previousElement.getId());
                        nextLevelComputeUnits.add(previousElement);
                    }
                }
            }
            levelComputeUnits = nextLevelComputeUnits;
        }
        return lastElement;
    }
}

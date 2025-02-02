package in.ramanujan.rule.engine;


import java.util.HashSet;
import java.util.Set;

public class ProcessorManager {
    private static Set<String> disabledProcesses = new HashSet<>();


    public static void disableProcess(final String processId) {
        disabledProcesses.add(processId);
    }

    public static Boolean isProcessDisabled(final String processId) {
        return disabledProcesses.contains(processId);
    }
}

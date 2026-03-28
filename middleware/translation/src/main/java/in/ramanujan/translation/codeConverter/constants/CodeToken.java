package in.ramanujan.translation.codeConverter.constants;

public class CodeToken {
    //Tokens related to thread-management
    public static String threadStart = "threadStart";
    public static String threadEnd = "threadComplete";
    public static String threadTriggerOnSomeThreadCompleteion = "threadOnEnd";
    /**
     * threadParallelismCycle: runs its body after EVERY completed cycle of parallelism
     * (i.e. every time all listed threads finish one iteration), unlike threadOnEnd which
     * only runs its body on the LAST cycle.
     * Syntax: threadParallelismCycle(t1, t2, ..., N) { ... }
     */
    public static String threadParallelismCycle = "threadParallelismCycle";

    //Tokens related to function declaration
    public static String functionDef = "def";
    public static String functionExec = "exec";
}

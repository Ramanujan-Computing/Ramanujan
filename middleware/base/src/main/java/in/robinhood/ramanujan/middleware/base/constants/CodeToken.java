package in.robinhood.ramanujan.middleware.base.constants;

public class CodeToken {
    //Tokens related to thread-management
    public static String threadStart = "threadStart";
    public static String threadEnd = "threadComplete";
    public static String threadTriggerOnSomeThreadCompleteion = "threadOnEnd";

    //Tokens related to function declaration
    public static String functionDef = "def";
    public static String functionExec = "exec";
}

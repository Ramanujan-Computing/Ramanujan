package in.ramanujan.rule.engine;

import java.util.ArrayList;
import java.util.HashMap;

public class NativeDebugger {
    /**
     * cpp native lib exposes following object:
     * list<double> beforeVal;
     *     list<double> afterVal;
     *     bool condResult;
     *     string commandId;
     *     int line = 0;
     *
     *     list<double> currentFuncVal;
     *     unordered_map<string, string> arrayInFuncCall;
     *
     *     we will have same fields.
     *
     *
     */

    public String commandId;
    public int line;
    public boolean condResult;
    public HashMap arrayInFuncCall;
    public ArrayList beforeVal;
    public ArrayList afterVal;
    public ArrayList currentFuncVal;

}
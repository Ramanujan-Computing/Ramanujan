package in.ramanujan.rule.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.load;
import static java.lang.System.loadLibrary;

public class NativeProcessor {
    public native void process(String ruleEngineInputJson, String firstCommandId);

    public HashMap jniObject;
    public ArrayList debugPoints;

    static {
//        loadLibrary("native");
        load("/Users/pranav/Desktop/ramanujan/ramanujan-native/native/cmake-build-debug/libnative.dylib");
    }
}

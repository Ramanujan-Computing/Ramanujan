package in.ramanujan.rule.engine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.load;
import static java.lang.System.loadLibrary;

import in.ramanujan.rule.engine.util.NativeLibraryLoader;
import java.io.IOException;

public class NativeProcessor {
    public native void process(String ruleEngineInputJson, String firstCommandId);

    public HashMap jniObject;
    public ArrayList debugPoints;

    static {
        try {
            NativeLibraryLoader.load("native");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library", e);
        }
    }
}

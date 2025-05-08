package in.ramanujan.rule.engine;

import java.lang.reflect.Field;
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
        String nativeLibPath = System.getenv("NATIVE_LIB_PATH");
        if (nativeLibPath != null) {
            System.out.println("Setting java.library.path to " + nativeLibPath);
            System.setProperty("java.library.path", nativeLibPath);
            // This is necessary to reset the library path
            try {
                final Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
                fieldSysPath.setAccessible(true);
                fieldSysPath.set(null, null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset library path", e);
            }
            loadLibrary("native");
        } else {
            System.out.println("NATIVE_LIB_PATH not set, skipping loading of native library");
        }

    }
}

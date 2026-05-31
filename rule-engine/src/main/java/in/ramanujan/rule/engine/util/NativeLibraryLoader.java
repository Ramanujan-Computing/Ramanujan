package in.ramanujan.rule.engine.util;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;

public class NativeLibraryLoader {
    private static final String ENV_TMP_DIR = "RAMANUJAN_WS"; // Change as needed

    public static void load(String libBaseName) throws IOException {
        // Check if its running in android, if so, return
        if (System.getProperty("java.runtime.name").toLowerCase(Locale.ROOT).contains("android")) {
            return; // Android environment, skip loading native libraries
        }
        String libFileName = System.mapLibraryName(libBaseName);
        String wsDir = System.getenv(ENV_TMP_DIR);
        if (wsDir == null || wsDir.isEmpty()) {
            throw new IOException("Environment variable '" + ENV_TMP_DIR + "' is not set");
        }
        Path libPath = Paths.get(wsDir, libFileName);
        if (!Files.exists(libPath)) {
            throw new FileNotFoundException("Native library not found in RAMANUJAN_WS: " + libPath);
        }
        System.load(libPath.toAbsolutePath().toString());
    }

    private static String detectOS() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        if (os.contains("nux")) return "linux";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (arch.contains("64")) return "x86_64";
        if (arch.contains("86")) return "x86";
        if (arch.contains("arm")) return "arm";
        throw new UnsupportedOperationException("Unsupported architecture: " + arch);
    }

    private static String getLibExtension(String os) {
        switch (os) {
            case "windows": return ".dll";
            case "macos": return ".dylib";
            case "linux": return ".so";
            default: throw new IllegalArgumentException("Unknown OS: " + os);
        }
    }

    private static boolean isExecutable(String ext) {
        return ".so".equals(ext) || ".dylib".equals(ext);
    }
}

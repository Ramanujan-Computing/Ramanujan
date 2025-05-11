package in.ramanujan.rule.engine.util;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;

public class NativeLibraryLoader {
    private static final String ENV_TMP_DIR = "RAMANUJAN_WS"; // Change as needed

    public static void load(String libBaseName) throws IOException {
        String os = detectOS();
        String arch = detectArch();
        String ext = getLibExtension(os);
        String libFileName = System.mapLibraryName(libBaseName);
        String resourcePath = String.format("/native/%s/%s/%s", os, arch, libFileName);

        String tmpDir = System.getenv(ENV_TMP_DIR);
        if (tmpDir == null || tmpDir.isEmpty()) {
            throw new IOException("Environment variable '" + ENV_TMP_DIR + "' is not set");
        }
        Path targetPath = Paths.get(tmpDir, libFileName);
        if (!Files.exists(targetPath)) {
            try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new FileNotFoundException("Native library not found in resources: " + resourcePath);
                }
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!isExecutable(ext)) {
                targetPath.toFile().setReadable(true);
                targetPath.toFile().setWritable(true);
            } else {
                targetPath.toFile().setExecutable(true);
            }
        }
        System.load(targetPath.toAbsolutePath().toString());
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

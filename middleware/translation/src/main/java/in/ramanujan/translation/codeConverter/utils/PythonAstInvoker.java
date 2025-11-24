package in.ramanujan.translation.codeConverter.utils;

import in.ramanujan.translation.codeConverter.exception.CompilationException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class PythonAstInvoker {
    
    public String invokeAst(String pythonCode) throws CompilationException {
        Path tempFile = null;
        try {
            // Create temporary file
            tempFile = Files.createTempFile("ramanujan_python_", ".py");
            Files.write(tempFile, pythonCode.getBytes());
            
            // Execute python3 -m ast
            ProcessBuilder pb = new ProcessBuilder("python3", "-m", "ast", tempFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new CompilationException(null, null, 
                    "Python AST generation failed: " + output.toString());
            }
            
            return output.toString();
            
        } catch (IOException | InterruptedException e) {
            throw new CompilationException(null, null, 
                "Failed to invoke Python AST: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        }
    }
}

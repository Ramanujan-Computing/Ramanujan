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

    /**
     * Invokes Python to generate AST JSON using ast2json.
     * Requires ast2json to be installed in the Python environment.
     */
    public String invokeAstJson(String pythonCode) throws CompilationException {
        Path tempPy = null;
        try {
            tempPy = Files.createTempFile("ramanujan_python_", ".py");
            Files.write(tempPy, pythonCode.getBytes());

            // Python snippet: read file, parse AST, convert to JSON via ast2json
            String pySnippet = String.join("\n",
                "import sys, json, ast",
                "try:",
                "    import ast2json",
                "except ImportError:",
                "    sys.stderr.write('ast2json not installed\\n')",
                "    sys.exit(2)",
                "path = sys.argv[1]",
                "with open(path, 'r', encoding='utf-8') as f:",
                "    code = f.read()",
                "tree = ast.parse(code)",
                "data = ast2json.ast2json(tree)",
                "print(json.dumps(data))"
            );

            ProcessBuilder pb = new ProcessBuilder("python3", "-c", pySnippet, tempPy.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new CompilationException(null, null, "Python AST JSON generation failed: " + output.toString());
            }
            return output.toString().trim();
        } catch (IOException | InterruptedException e) {
            throw new CompilationException(null, null, "Failed to invoke Python ast2json: " + e.getMessage());
        } finally {
            if (tempPy != null) {
                try { Files.deleteIfExists(tempPy); } catch (IOException ignored) {}
            }
        }
    }
}

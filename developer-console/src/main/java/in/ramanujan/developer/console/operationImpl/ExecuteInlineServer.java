package in.ramanujan.developer.console.operationImpl;

import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import in.ramanujan.translation.codeConverter.utils.TranslateUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static in.ramanujan.developer.console.operationImpl.ExecutorImpl.createJson;

/**
 * Persistent server mode: one JVM stays alive for the entire inference run.
 * Reads newline-delimited commands from stdin:
 *
 *   run <kernel.py> <csv1> <csv2> ...   — compile and execute a kernel
 *   dump <name> [file]                  — dump array to stdout or file
 *   var  <name>                         — print scalar variable value
 *   arr  <name> <index>                 — print one array element
 *   quit                                — exit cleanly
 *
 * After each `run` command completes, prints exactly one line: "KERNEL_DONE"
 * After each `dump <name> <file>`, prints: "Dumped <name> (<size>) to <file>"
 * On error, prints: "KERNEL_ERROR: <message>"
 *
 * Benefits vs per-call JVM:
 *   - JVM startup paid once (~5-8s)
 *   - OpenCL context initialised once (clBuildProgram cached in s_programCache)
 *   - No process spawn overhead between layers
 */
public class ExecuteInlineServer extends ExecuteInline {

    // Compiled DAG cache — reused across calls for the same kernel file.
    // The server is single-threaded (stdin loop), so no synchronisation needed.
    private String               cachedKernelPath  = null;
    private DagElement           cachedFirstDag    = null;
    private List<DagElement>     cachedDagList     = null;
    private Map<String, Variable> cachedVariableMap = null;
    private Map<String, Array>    cachedArrayMap    = null;

    @Override
    public void execute(List<String> args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        // Signal readiness so the Python orchestrator knows the JVM is up
        System.out.println("SERVER_READY");
        System.out.flush();

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                System.out.println("SERVER_EXIT");
                System.out.flush();
                break;
            }

            if (line.startsWith("run ")) {
                // Parse: run <kernel.py> <csv1> <csv2> ...
                String[] parts = line.split("\\s+");
                List<String> kernelArgs = new ArrayList<>(parts.length - 1);
                for (int i = 1; i < parts.length; i++) {
                    kernelArgs.add(parts[i]);
                }
                try {
                    runKernel(kernelArgs);
                    System.out.println("KERNEL_DONE");
                } catch (Exception e) {
                    System.out.println("KERNEL_ERROR: " + e.getMessage());
                }
                System.out.flush();
                continue;
            }

            // Query commands (dump / var / arr) reuse shared helper
            handleQueryCommand(line);
            System.out.flush();
        }
    }

    // -------------------------------------------------------------------------
    // Core kernel execution (mirrors ExecuteInline.execute but no console loop)
    // -------------------------------------------------------------------------

    private void runKernel(List<String> args) throws IOException, CompilationException {
        long t0 = System.currentTimeMillis();

        System.err.println("[Server] run: " + args.get(0) + " +" + (args.size() - 1) + " CSVs");

        CodeRunRequest req = createJson(args);
        System.err.println("[Server] createJson: " + (System.currentTimeMillis() - t0) + "ms");
        String kernelPath = args.get(0);
        List<CsvInformation> csvList = req.getCsvInformationList() != null
                ? req.getCsvInformationList() : new ArrayList<>();

        Map<String, Variable> variableMap;
        Map<String, Array>    arrayMap;
        DagElement            firstDag;
        List<DagElement>      dagList;

        boolean cacheHit = kernelPath.equals(cachedKernelPath) && cachedFirstDag != null;
        if (cacheHit) {
            variableMap = cachedVariableMap;
            arrayMap    = cachedArrayMap;
            firstDag    = cachedFirstDag;
            dagList     = cachedDagList;

            // Clear mutable state so re-population starts clean
            for (Array a : arrayMap.values()) {
                if (a.getValues() != null) a.getValues().clear();
                a.setBinaryFile(null);
            }
            for (Variable v : variableMap.values()) {
                v.setValue(null);
            }

            long repopStart = System.currentTimeMillis();
            translateUtil.repopulateCsvArrayValues(arrayMap, csvList);
            System.err.println("[Server] compiled in (cached) " + (System.currentTimeMillis() - t0)
                    + "ms  repopulate=" + (System.currentTimeMillis() - repopStart) + "ms");
        } else {
            variableMap = new HashMap<>();
            arrayMap    = new HashMap<>();

            String code = req.getCode();
            Map<String, RuleEngineInput> functionCallsRuleEngineInput = new HashMap<>();
            ActualDebugCodeCreator debugCreator = new ActualDebugCodeCreator("", 0);

            String extractedCode;
            int linesForFunctions;

            if (TranslateUtil.isPythonCode(code)) {
                extractedCode = code;
                linesForFunctions = 0;
            } else {
                ExtractedCodeAndFunctionCode extracted =
                        translateUtil.extractCodeWithoutAbstractCodeDeclaration(
                                code, functionCallsRuleEngineInput, debugCreator);
                for (Map.Entry<String, RuleEngineInput> e : functionCallsRuleEngineInput.entrySet()) {
                    for (Variable v : e.getValue().getVariables()) variableMap.put(v.getId(), v);
                    for (Array a : e.getValue().getArrays())        arrayMap.put(a.getId(), a);
                }
                extractedCode = extracted.getExtractedCode();
                linesForFunctions = debugCreator.getLine();
            }

            CodeSnippetElement firstSnippet = translateUtil.getCodeSnippets(
                    extractedCode, new HashMap<>(), new HashMap<>(), new HashMap<>());

            dagList = new ArrayList<>();
            Map<String, String> dagCodeMap = new HashMap<>();
            firstDag = translateUtil.populateAllDagElements(
                    firstSnippet, csvList, functionCallsRuleEngineInput,
                    variableMap, arrayMap, dagList, dagCodeMap, linesForFunctions);

            System.err.println("[Server] compiled in " + (System.currentTimeMillis() - t0)
                    + "ms  DAG=" + (dagList.size() + 1));

            cachedKernelPath  = kernelPath;
            cachedFirstDag    = firstDag;
            cachedDagList     = dagList;
            cachedVariableMap = variableMap;
            cachedArrayMap    = arrayMap;
        }

        long execStart = System.currentTimeMillis();
        boolean sequential = "true".equalsIgnoreCase(System.getenv("RAMANUJAN_SEQUENTIAL"));
        if (sequential) {
            executeSequentially(firstDag, dagList, variableMap, arrayMap);
        } else {
            executeInParallel(firstDag, dagList, variableMap, arrayMap);
        }
        System.err.println("[Server] executed in " + (System.currentTimeMillis() - execStart) + "ms");

        // Build stores so query commands can access results
        Map<String, Object> varStore = new HashMap<>();
        for (Variable v : variableMap.values()) varStore.put(v.getName(), v.getValue());


        Map<String, Map<String, Object>> arrStore = new HashMap<>();
        for (Array a : arrayMap.values()) {
            String id = a.getId();
            if (id.contains("func") || !id.contains("_name_")) continue;
            String name = id.split("_name_")[1];
            // Only populate generated_tokens to save time!
            if (!name.equals("generated_tokens") && !name.equals("hidden") && !name.equals("debug_out")) continue;
            
            Map<String, Object> vals = a.getValues();
            if (vals == null) continue;
            for (Map.Entry<String, Object> e : vals.entrySet()) {
                Map<String, Object> m = arrStore.computeIfAbsent(name, k -> new HashMap<>());
                m.put(e.getKey(), e.getValue());
            }
        }

        ExecutorImpl.setStores(varStore, arrStore);
        // Hint GC to release large stub strings and old computation objects from this kernel.
        // Without this, hundreds of MB of zero-grid strings accumulate causing GC storms.
        System.gc();
    }

    // -------------------------------------------------------------------------
    // Query command handler (dump / var / arr)
    // -------------------------------------------------------------------------

    private void handleQueryCommand(String line) {
        if (line.startsWith("var ")) {
            String[] p = line.split(" ", 2);
            if (p.length == 2) {
                Object v = ExecutorImpl.variableStore.get(p[1]);
                System.out.println(v != null ? p[1] + " = " + v : "Variable not found.");
            }
            return;
        }

        if (line.startsWith("arr ")) {
            String[] p = line.split(" ");
            if (p.length == 3) {
                Map<String, Object> arr = ExecutorImpl.arrayStore.get(p[1]);
                if (arr != null && arr.containsKey(p[2])) {
                    System.out.println(p[1] + "[" + p[2] + "] = " + arr.get(p[2]));
                } else {
                    System.out.println("Array or index not found.");
                }
            }
            return;
        }

        if (line.startsWith("dump ")) {
            String[] p = line.split(" ");
            String arrName = p.length >= 2 ? p[1] : null;
            String outFile = p.length >= 3 ? p[2] : null;
            if (arrName == null) {
                System.out.println("Usage: dump <arrayName> [outputFile]");
                return;
            }
            Map<String, Object> arr = ExecutorImpl.arrayStore.get(arrName);
            if (arr == null || arr.isEmpty()) {
                System.out.println("Array not found or empty: " + arrName);
                return;
            }

            // Detect dimensionality from key format
            boolean is1D = true;
            int maxRow = 0, maxCol = 0;
            for (String key : arr.keySet()) {
                String[] dims = key.split("_");
                if (dims.length >= 2) {
                    is1D = false;
                    maxRow = Math.max(maxRow, Integer.parseInt(dims[0]));
                    maxCol = Math.max(maxCol, Integer.parseInt(dims[1]));
                } else {
                    maxRow = Math.max(maxRow, Integer.parseInt(dims[0]));
                }
            }

            StringBuilder csv = new StringBuilder();
            if (is1D) {
                for (int i = 0; i <= maxRow; i++) {
                    if (i > 0) csv.append(',');
                    Object v = arr.get(String.valueOf(i));
                    csv.append(v != null ? v : "0.0");
                }
                csv.append('\n');
            } else {
                for (int r = 0; r <= maxRow; r++) {
                    for (int c = 0; c <= maxCol; c++) {
                        if (c > 0) csv.append(',');
                        Object v = arr.get(r + "_" + c);
                        csv.append(v != null ? v : "0.0");
                    }
                    csv.append('\n');
                }
            }

            String dims = is1D ? String.valueOf(maxRow + 1) : (maxRow + 1) + "x" + (maxCol + 1);
            if (outFile != null) {
                try {
                    Files.write(Paths.get(outFile),
                            csv.toString().getBytes(StandardCharsets.UTF_8));
                    System.out.println("Dumped " + arrName + " (" + dims + ") to " + outFile);
                } catch (Exception e) {
                    System.out.println("Error writing file: " + e.getMessage());
                }
            } else {
                System.out.print(csv.toString());
            }
            return;
        }

        System.out.println("Unknown command: " + line);
    }
}

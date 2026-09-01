package in.ramanujan.translation.codeConverter.utils;

import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.MethodDataTypeAgnosticArg;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.codeConverterLogicImpl.VariableInitLogicConverter;
import in.ramanujan.translation.codeConverter.constants.CodeToken;
import in.ramanujan.translation.codeConverter.grammar.CodeContainer;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.pojo.PairCodeSnippetElementWithParent;
import in.ramanujan.translation.codeConverter.pojo.StringWrapper;
import in.ramanujan.translation.codeConverter.exception.CompilationException;

import java.util.*;

public class TranslateUtil {

    private final CodeConverterLogicFactory codeConverterLogicFactory = new CodeConverterLogicFactory();

    /*
    * Traverses the graph of CodeSnippetElement object, and convert each CodeSnippetElement object to DagElement object,
    * in the process, making the graph of it. Returns the entry point to the graph created
    * */
    public DagElement populateAllDagElements(CodeSnippetElement codeSnippetElement, List<CsvInformation> csvInformationList,
                                             Map<String, RuleEngineInput> functionCallsRuleEngineInput,
                                             Map<String, Variable> variableMap, Map<String, Array> arrayMap,
                                             List<DagElement> dagElementListToBePopulated,
                                             Map<String, String> dagElementAndCodeMap, int linesForCommonFunctions) throws CompilationException {
        Queue<PairCodeSnippetElementWithParent> populationQueue = new LinkedList<PairCodeSnippetElementWithParent>();
        CodeConverter codeConverter = getNewCodeConverter(csvInformationList);
        populationQueue.add(new PairCodeSnippetElementWithParent(codeSnippetElement, null));
        DagElement lastElement = null;
        Map<String, DagElement> codeSnippetElementDagElementMap = new HashMap<>();
        boolean isFirstDagElementBeingCreated = true;
        RuleEngineInput pythonMainRuleEngineInput = null;
        while(populationQueue.size() > 0) {
            PairCodeSnippetElementWithParent pairCodeSnippetElementWithParent = populationQueue.poll();
            DagElement dagElement = codeSnippetElementDagElementMap.get(pairCodeSnippetElementWithParent
                    .getCodeSnippetElement().getUuid());
            if(dagElement == null) {
                RuleEngineInput ruleEngineInput = new RuleEngineInput();

                final ActualDebugCodeCreator actualDebugCodeCreator = new ActualDebugCodeCreator("", linesForCommonFunctions);
                String code = pairCodeSnippetElementWithParent.getCodeSnippetElement().getCode();

                // For the first code snippet, if it is Python, prepend 2D array declarations for each CSV
                // (declaration only — no per-element assignments; values are bulk-populated after interpretation)
                if (isFirstDagElementBeingCreated && isPythonCode(code) && csvInformationList != null && !csvInformationList.isEmpty()) {
                    System.out.println("[TranslateUtil] Generating CSV declarations for " + csvInformationList.size() + " files");
                    System.out.flush();
                    long declStart = System.currentTimeMillis();
                    String csvDeclCode = generateCsvDeclPythonCode(csvInformationList);
                    System.out.println("[TranslateUtil] CSV declarations generated in " + (System.currentTimeMillis() - declStart) + "ms (" + csvDeclCode.length() + " chars)");
                    System.out.flush();
                    if (!csvDeclCode.isEmpty()) {
                        code = csvDeclCode + code;
                    }
                }

                // Detect if code is Python or Ramanujan and call appropriate method
                List<Command> commands = null;
                if (isPythonCode(code)) {
                    Map<Integer, RuleEngineInputUnits> functionFrameVariableMap = new HashMap<>();
                    Integer[] frameVariableCounterId = {0};
                    System.out.println("[TranslateUtil] Starting interpretPython (code length=" + code.length() + " chars)");
                    System.out.flush();
                    long interpStart = System.currentTimeMillis();
                    commands = codeConverter.interpretPython(
                            code, ruleEngineInput,
                            new ArrayList<>(),
                            actualDebugCodeCreator, functionFrameVariableMap, frameVariableCounterId);
                    System.out.println("[TranslateUtil] interpretPython completed in " + (System.currentTimeMillis() - interpStart) + "ms, commands=" + (commands == null ? "null" : commands.size()));
                    System.out.println("[TranslateUtil]   Variables: " + ruleEngineInput.getVariables().size() + ", Arrays: " + ruleEngineInput.getArrays().size());
                    System.out.flush();
                    // Bulk-populate CSV array values directly (bypasses interpreter for millions of assignments)
                    if (isFirstDagElementBeingCreated && csvInformationList != null && !csvInformationList.isEmpty()) {
                        System.out.println("[TranslateUtil] Starting directPopulateCsvArrayValues for " + csvInformationList.size() + " CSVs");
                        System.out.flush();
                        long popStart = System.currentTimeMillis();
                        directPopulateCsvArrayValues(ruleEngineInput, csvInformationList);
                        System.out.println("[TranslateUtil] directPopulateCsvArrayValues completed in " + (System.currentTimeMillis() - popStart) + "ms");
                        System.out.flush();
                    }
                } else {
                    codeConverter.interpret(
                            code, ruleEngineInput,
                            new LinkedList<String>() {{add("");}},
                            actualDebugCodeCreator, null, null);
                }

               dagElement = new DagElement(ruleEngineInput);
               dagElementAndCodeMap.put(dagElement.getId(), actualDebugCodeCreator.getDebugCode());

               // For Python code, use the first command from the returned commands list (execution order)
               // For Ramanujan code, use the first command from ruleEngineInput.getCommands() list
               if(commands != null && commands.size() > 0) {
                   dagElement.setFirstCommandId(commands.get(0).getId());
               } else if(ruleEngineInput.getCommands().size() > 0) {
                   dagElement.setFirstCommandId(ruleEngineInput.getCommands().get(0).getId());
               } else {
                   dagElement.setFirstCommandId("");
               }

                for(RuleEngineInput ruleEngineInputFunction : functionCallsRuleEngineInput.values()) {
                    ruleEngineInput.addAllPartsOfGivenRuleEngineInput(ruleEngineInputFunction);
                }

                // For Python code: propagate function definitions and global state (variables,
                // arrays, function bodies) from the first DAG element into every child DAG element
                // (threadStart / threadParallelismCycle bodies). This is needed because Python
                // function defs are added to the first element's RuleEngineInput by
                // PythonAstToRuleEngineInputConverter, but child elements get fresh, empty
                // RuleEngineInputs and would otherwise be unable to resolve any function calls.
                if (isPythonCode(code)) {
                    if (isFirstDagElementBeingCreated) {
                        pythonMainRuleEngineInput = ruleEngineInput;
                    } else if (pythonMainRuleEngineInput != null) {
                        ruleEngineInput.addAllPartsOfGivenRuleEngineInput(pythonMainRuleEngineInput);
                    }
                }

            }
            DagElement parentDagElement = pairCodeSnippetElementWithParent.getDagElement();
            if(parentDagElement != null) {
                /*
                * Connects the previous dagElement with the current dagElement
                * */
                dagElement.getPreviousElementIds().add(parentDagElement.getId());
                dagElement.getPreviousElements().add(parentDagElement);
                parentDagElement.getNextElements().add(dagElement);
            }
            if(!codeSnippetElementDagElementMap.containsKey(pairCodeSnippetElementWithParent.getCodeSnippetElement().getUuid())) {
                for (CodeSnippetElement childCodeSnippetElement : pairCodeSnippetElementWithParent.getCodeSnippetElement().getNext()) {
                    populationQueue.add(new PairCodeSnippetElementWithParent(childCodeSnippetElement, dagElement));
                }
            }
            codeSnippetElementDagElementMap.put(pairCodeSnippetElementWithParent.getCodeSnippetElement().getUuid(), dagElement);
            variableMap.putAll(dagElement.getVariableMap());
            arrayMap.putAll(dagElement.getArrayMap());
            lastElement = dagElement;


            isFirstDagElementBeingCreated = false;
        }
        return  DagUtils.getFirstElementOfDag(lastElement, dagElementListToBePopulated);
    }

    public CodeConverter getNewCodeConverter(List<CsvInformation> csvInformationList) {
        return new CodeConverter(codeConverterLogicFactory, null, csvInformationList);
    }

    /**
     * Extracts a clean Python identifier from a CSV filename.
     * Strips directory path, .csv extension, and replaces invalid chars with underscores.
     */
    private static String csvFileNameToArrayName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return null;
        // Strip directory path — use only the basename
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        if (fileName.endsWith(".csv")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        String name = fileName.replaceAll("[^a-zA-Z0-9_]", "_");
        return name.isEmpty() ? null : name;
    }

    /**
     * Generates ONLY the 2D array declaration for each CSV (no per-element assignments).
     * The actual values are bulk-populated via directPopulateCsvArrayValues() after interpretation.
     */
    private String generateCsvDeclPythonCode(List<CsvInformation> csvInformationList) {
        StringBuilder sb = new StringBuilder();
        for (CsvInformation csvInformation : csvInformationList) {
            String data = csvInformation.getData();
            if (data == null || data.trim().isEmpty()) continue;

            String arrayName = csvFileNameToArrayName(csvInformation.getFileName());
            if (arrayName == null) continue;

            // Count rows and columns
            int firstNewline = data.indexOf('\n');
            String firstRow = (firstNewline >= 0) ? data.substring(0, firstNewline) : data;
            int numCols = 1;
            for (int i = 0; i < firstRow.length(); i++) {
                if (firstRow.charAt(i) == ',') numCols++;
            }
            int numRows = 1;
            for (int i = 0; i < data.length(); i++) {
                if (data.charAt(i) == '\n') numRows++;
            }
            // Trim trailing empty row
            if (data.endsWith("\n")) numRows--;

            if (numRows == 1) {
                sb.append(arrayName)
                  .append(" = [0 for _ in range(").append(numCols).append(")]\n");
            } else {
                sb.append(arrayName)
                  .append(" = [[0 for _ in range(").append(numCols).append(")]")
                  .append(" for _ in range(").append(numRows).append(")]\n");
            }
        }
        return sb.toString();
    }

    /**
     * Directly populates Array objects' values maps from CSV data, bypassing the interpreter.
     * This is O(n) with just string parsing and map puts — no AST, no interpreter overhead.
     * For a 50M-element CSV, this saves ~50M interpreted Python assignment statements.
     * Multiple CSVs are processed in parallel using threads.
     */
    private void directPopulateCsvArrayValues(RuleEngineInput ruleEngineInput, List<CsvInformation> csvInformationList) {
        // Build a lookup: arrayName -> Array object (using the _name_ convention)
        Map<String, Array> arrayByName = new HashMap<>();
        for (Array array : ruleEngineInput.getArrays()) {
            String id = array.getId();
            if (id != null && id.contains("_name_")) {
                String name = id.split("_name_")[1];
                arrayByName.put(name, array);
            }
        }

        // Process CSVs in parallel
        int nThreads = Math.min(csvInformationList.size(), Runtime.getRuntime().availableProcessors());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, nThreads));
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(csvInformationList.size());

        for (CsvInformation csvInformation : csvInformationList) {
            final CsvInformation csv = csvInformation;
            pool.submit(() -> {
                try {
                    populateSingleCsvArray(csv, arrayByName);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdown();
    }

    /**
     * Parses a single CSV and populates the corresponding Array's values map.
     */
    private void populateSingleCsvArray(CsvInformation csvInformation, Map<String, Array> arrayByName) {
        String arrayName = csvFileNameToArrayName(csvInformation.getFileName());
        if (arrayName == null) return;

        Array array = arrayByName.get(arrayName);
        if (array == null) {
            System.err.println("[TranslateUtil] WARNING: No Array object found for CSV '" + arrayName + "', skipping");
            return;
        }

        // Check for pre-existing .bin file (pre-converted offline or written by a prior call).
        // Also resolve symlinks so that temp-dir .csv symlinks find the real .bin.
        String csvPath = csvInformation.getFileName();
        // sidecarBinPath is the persistent .bin we write next to the CSV for reuse across calls.
        String sidecarBinPath = null;
        if (csvPath != null && csvPath.endsWith(".csv")) {
            java.io.File csvFile = new java.io.File(csvPath);

            // 1. Try next to the CSV as-is
            java.io.File binFile = new java.io.File(csvPath.substring(0, csvPath.length() - 4) + ".bin");
            sidecarBinPath = binFile.getAbsolutePath();
            if (!binFile.exists() || binFile.length() == 0) {
                // 2. Resolve symlink and try next to the real file
                try {
                    java.nio.file.Path real = java.nio.file.Paths.get(csvPath).toRealPath();
                    String realStr = real.toString();
                    binFile = new java.io.File(realStr.substring(0, realStr.length() - 4) + ".bin");
                    sidecarBinPath = binFile.getAbsolutePath();
                    csvFile = real.toFile();
                } catch (Exception ignored) {}
            }
            if (binFile.exists() && binFile.length() > 0) {
                // mtime guard: only reuse the bin if it is at least as new as the CSV.
                // This ensures that updated hidden-state CSVs (new activations written each layer)
                // are never served stale binary data.
                if (binFile.lastModified() >= csvFile.lastModified()) {
                    String absoluteBinPath;
                    try { absoluteBinPath = binFile.getCanonicalPath(); } catch (Exception e) { absoluteBinPath = binFile.getAbsolutePath(); }
                    System.out.println("[TranslateUtil] Sidecar hit (fresh): " + absoluteBinPath + " (" + (binFile.length() / 1024 / 1024) + " MB)");
                    array.setBinaryFile(absoluteBinPath);
                    return;
                } else {
                    System.out.println("[TranslateUtil] Sidecar stale for '" + arrayName + "' (csv newer), will re-parse and overwrite");
                }
            }
        }

        String data = csvInformation.getData();
        if (data == null || data.trim().isEmpty()) return;

        // Detect if single-row (no newlines except possibly trailing)
        String trimmedData = data.endsWith("\n") ? data.substring(0, data.length() - 1) : data;
        boolean singleRow = (trimmedData.indexOf('\n') < 0);

        // Estimate value count from data size (rough: ~11 chars per float value)
        long estimatedValues = data.length() / 8;

        // For large arrays (>100K values), write binary file instead of populating HashMap
        if (estimatedValues > 100000) {
            try {
                populateLargeArrayAsBinary(data, singleRow, array, arrayName, sidecarBinPath);
                return;
            } catch (Exception e) {
                System.err.println("[TranslateUtil] Binary write failed for '" + arrayName + "', falling back to HashMap: " + e.getMessage());
                // Fall through to HashMap population
            }
        }

        // Fast CSV parsing: parse directly into the values map (ConcurrentHashMap — thread-safe)
        Map<String, Object> values = array.getValues();

        int row = 0;
        int col = 0;
        int start = 0;
        int len = data.length();
        for (int i = 0; i <= len; i++) {
            char c = (i < len) ? data.charAt(i) : '\n';
            if (c == ',' || c == '\n') {
                if (i > start) {
                    String valStr = data.substring(start, i).trim();
                    if (!valStr.isEmpty()) {
                        double val = Double.parseDouble(valStr);
                        if (singleRow) {
                            values.put(String.valueOf(col), val);
                        } else {
                            values.put(row + "_" + col, val);
                        }
                    }
                }
                if (c == ',') {
                    col++;
                } else {
                    row++;
                    col = 0;
                }
                start = i + 1;
            }
        }
        System.out.println("[TranslateUtil] Direct-populated array '" + arrayName + "' with " + values.size() + " values");
    }

    /**
     * Write large CSV array data as a binary float32 file and set the binaryFile
     * path on the Array object. The native C++ side will load this directly,
     * bypassing JSON serialization entirely.
     */
    private void populateLargeArrayAsBinary(String data, boolean singleRow, Array array, String arrayName,
                                             String sidecarPath) throws Exception {
        long t0 = System.currentTimeMillis();

        // Prefer writing next to the CSV (sidecar) so subsequent calls find the bin via the
        // existing fast-path check in populateSingleCsvArray and skip re-parsing entirely.
        // Fall back to a temp file if the directory is not writable.
        java.io.File tmpFile;
        boolean usingSidecar = false;
        if (sidecarPath != null) {
            java.io.File sidecarFile = new java.io.File(sidecarPath);
            java.io.File parentDir = sidecarFile.getParentFile();
            if (parentDir != null && parentDir.canWrite()) {
                tmpFile = sidecarFile;
                usingSidecar = true;
            } else {
                tmpFile = java.io.File.createTempFile("rj_bin_" + arrayName + "_", ".bin");
                tmpFile.deleteOnExit();
            }
        } else {
            tmpFile = java.io.File.createTempFile("rj_bin_" + arrayName + "_", ".bin");
            tmpFile.deleteOnExit();
        }

        // Parse CSV and write as flat float32 (little-endian)
        int valueCount = 0;
        try (java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(
                new java.io.FileOutputStream(tmpFile), 1024 * 1024)) {

            int start = 0;
            int len = data.length();
            byte[] buf = new byte[4]; // float32

            for (int i = 0; i <= len; i++) {
                char c = (i < len) ? data.charAt(i) : '\n';
                if (c == ',' || c == '\n') {
                    if (i > start) {
                        // Fast inline trim
                        int s = start, e = i;
                        while (s < e && data.charAt(s) <= ' ') s++;
                        while (e > s && data.charAt(e - 1) <= ' ') e--;
                        if (s < e) {
                            float val = Float.parseFloat(data.substring(s, e));
                            int bits = Float.floatToRawIntBits(val);
                            buf[0] = (byte) (bits);
                            buf[1] = (byte) (bits >> 8);
                            buf[2] = (byte) (bits >> 16);
                            buf[3] = (byte) (bits >> 24);
                            bos.write(buf);
                            valueCount++;
                        }
                    }
                    start = i + 1;
                }
            }
        }

        // Set the binary file path on the Array — native side will load from here
        array.setBinaryFile(tmpFile.getAbsolutePath());
        // Clear the values map to avoid JSON serialization of data
        array.getValues().clear();

        long elapsed = System.currentTimeMillis() - t0;
        System.out.println("[TranslateUtil] Binary-populated array '" + arrayName + "' with " + valueCount
                + " float32 values (" + (tmpFile.length() / 1024) + " KB) in " + elapsed + "ms"
                + (usingSidecar ? " [sidecar]" : " [tmp]") + " -> " + tmpFile.getAbsolutePath());
    }

    /**
     * Re-populates array values from fresh CSV data using an existing arrayMap.
     * Called on cache-hit paths where interpretPython is skipped — only the
     * per-call CSV values (e.g. hidden states) need to be refreshed.
     */
    public void repopulateCsvArrayValues(Map<String, Array> arrayMap, List<CsvInformation> csvList) {
        Map<String, Array> arrayByName = new HashMap<>();
        for (Map.Entry<String, Array> entry : arrayMap.entrySet()) {
            String id = entry.getKey();
            if (id != null && id.contains("_name_")) {
                arrayByName.put(id.split("_name_")[1], entry.getValue());
            }
        }
        int nThreads = Math.min(csvList.size(), Runtime.getRuntime().availableProcessors());
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, nThreads));
        java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(csvList.size());
        for (CsvInformation csv : csvList) {
            pool.submit(() -> {
                try { populateSingleCsvArray(csv, arrayByName); }
                finally { latch.countDown(); }
            });
        }
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        pool.shutdown();
    }

    /**
     * Detects whether the given code is Python or Ramanujan language.
     * This is a shared static method used by both TranslateUtil and TranslateService.
     * 
     * @param code The code to analyze
     * @return true if the code is Python, false if it's Ramanujan
     */
    public static boolean isPythonCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        
        String trimmedCode = code.trim();
        
        // Primary check: Ramanujan uses curly braces, Python doesn't.
        // However, threadStart(t) { ... } and threadParallelismCycle(t, n) { ... }
        // are Ramanujan threading constructs that CAN appear in Python scripts.
        // Strip those blocks before checking for stray braces.
        // Strip Python line comments (#...) before checking for braces, so that
        // notation like Σ_{k} in a comment does not fool the brace detector.
        String codeWithoutComments = trimmedCode.replaceAll("(?m)#[^\n]*", "");
        String codeWithoutThreadBlocks = removeThreadBlocks(codeWithoutComments);
        if (codeWithoutThreadBlocks.contains("{") || codeWithoutThreadBlocks.contains("}")) {
            return false;
        }
        
        // Check for Python-specific patterns
        // 1. Python function definitions with colon: def function_name(...):
        if (trimmedCode.matches("(?s).*\\bdef\\s+\\w+\\s*\\(.*\\)\\s*:.*")) {
            return true;
        }
        
        // 2. Python imports: import xyz or from xyz import
        if (trimmedCode.matches("(?s).*(^|\\n)\\s*(import\\s+\\w+|from\\s+\\w+\\s+import).*")) {
            return true;
        }
        
        // 3. Python class definitions: class ClassName:
        if (trimmedCode.matches("(?s).*\\bclass\\s+\\w+.*:.*")) {
            return true;
        }
        
        // 4. Python-style for/while loops with colon: for x in y: or while x:
        if (trimmedCode.matches("(?s).*(\\bfor\\s+.+\\s+in\\s+.+:|\\bwhile\\s+.+:).*")) {
            return true;
        }
        
        // 5. Check for Ramanujan-specific patterns
        // Variable declarations: var x:integer;
        if (trimmedCode.matches("(?s).*\\bvar\\s+\\w+\\s*:.*")) {
            return false;
        }
        
        // 6. If code has simple assignments without any language-specific syntax,
        // and no curly braces were found above, it's likely Python
        if (trimmedCode.matches("(?s).*(^|\\n)\\s*[a-zA-Z_]\\w*\\s*=\\s*.*")) {
            return true;
        }

        // 7. Python-style function calls: func_name() or func_name(args)
        // This covers code blocks inside threadStart that contain only function calls
        if (trimmedCode.matches("(?s).*(^|\\n)\\s*[a-zA-Z_]\\w*\\s*\\(.*\\).*")) {
            return true;
        }
        
        // Default to Ramanujan if no clear indicators
        return false;
    }

    private static String removeThreadBlocks(String code) {
        java.util.regex.Pattern header = java.util.regex.Pattern.compile(
                "\\b(?:threadStart|threadParallelismCycle|threadOnEnd)\\s*\\([^)]*\\)\\s*\\{");
        StringBuilder result = new StringBuilder(code.length());
        int copiedThrough = 0;
        java.util.regex.Matcher matcher = header.matcher(code);
        while (matcher.find(copiedThrough)) {
            result.append(code, copiedThrough, matcher.start());
            int depth = 1;
            int index = matcher.end();
            while (index < code.length() && depth > 0) {
                char current = code.charAt(index++);
                if (current == '{') depth++;
                else if (current == '}') depth--;
            }
            if (depth != 0) {
                result.append(code, matcher.start(), code.length());
                copiedThrough = code.length();
                break;
            }
            result.append('\n');
            copiedThrough = index;
        }
        result.append(code, copiedThrough, code.length());
        return result.toString();
    }

    private Boolean validateIfSuffixOfMethod(Character c) {
        return (!Character.isAlphabetic(c) && !Character.isDigit(c));
    }

    private String getToBeConsideredToken(String code, IndexWrapper threadStartCodeIndex, IndexWrapper threadEndCodeIndex,
                                          IndexWrapper threadParallelismCycleCodeIndex) {
        String toBeConsidered = "";

        // Find the earliest valid token among threadStart, threadOnEnd, threadParallelismCycle
        int startIdx = threadStartCodeIndex.getIndex();
        int endIdx   = threadEndCodeIndex.getIndex();
        int cycleIdx = threadParallelismCycleCodeIndex.getIndex();

        if(startIdx == -1 && endIdx == -1 && cycleIdx == -1) {
            return "";
        }

        // Pick the token with the smallest non-(-1) index
        int minIdx = Integer.MAX_VALUE;
        if(startIdx != -1) minIdx = Math.min(minIdx, startIdx);
        if(endIdx   != -1) minIdx = Math.min(minIdx, endIdx);
        if(cycleIdx != -1) minIdx = Math.min(minIdx, cycleIdx);

        if(minIdx == startIdx) {
            toBeConsidered = CodeToken.threadStart;
        } else if(minIdx == cycleIdx) {
            toBeConsidered = CodeToken.threadParallelismCycle;
        } else {
            toBeConsidered = CodeToken.threadTriggerOnSomeThreadCompleteion;
        }

        if(CodeToken.threadStart.equals(toBeConsidered)) {
            Boolean flag = true;
            int tmpIndex = threadStartCodeIndex.getIndex() + CodeToken.threadStart.length();
            if(threadStartCodeIndex.getIndex() > 0 && !validateIfSuffixOfMethod(code.charAt(threadStartCodeIndex.getIndex() - 1))) {
                toBeConsidered ="";
                threadStartCodeIndex.setIndex(code.indexOf(CodeToken.threadStart, threadStartCodeIndex.getIndex() + 1));
                flag = false;
            }
            while(flag && code.charAt(tmpIndex) != '(') {
                if(code.charAt(tmpIndex) != ' ') {
                    toBeConsidered ="";
                    threadStartCodeIndex.setIndex(code.indexOf(CodeToken.threadStart, threadStartCodeIndex.getIndex() + 1));
                    break;
                }
                tmpIndex++;
            }
        } else if(CodeToken.threadParallelismCycle.equals(toBeConsidered)) {
            Boolean flag = true;
            int tmpIndex = threadParallelismCycleCodeIndex.getIndex() + CodeToken.threadParallelismCycle.length();
            if(threadParallelismCycleCodeIndex.getIndex() > 0 && !validateIfSuffixOfMethod(code.charAt(threadParallelismCycleCodeIndex.getIndex() - 1))) {
                toBeConsidered ="";
                threadParallelismCycleCodeIndex.setIndex(code.indexOf(CodeToken.threadParallelismCycle, threadParallelismCycleCodeIndex.getIndex() + 1));
                flag = false;
            }
            while(flag && code.charAt(tmpIndex) != '(') {
                if(code.charAt(tmpIndex) != ' ') {
                    toBeConsidered ="";
                    threadParallelismCycleCodeIndex.setIndex(code.indexOf(CodeToken.threadParallelismCycle, threadParallelismCycleCodeIndex.getIndex() + 1));
                    break;
                }
                tmpIndex++;
            }
        } else {
            Boolean flag = true;
            int tmpIndex = threadEndCodeIndex.getIndex() + CodeToken.threadTriggerOnSomeThreadCompleteion.length();
            if(threadEndCodeIndex.getIndex() > 0 && !validateIfSuffixOfMethod(code.charAt(threadEndCodeIndex.getIndex() - 1))) {
                toBeConsidered ="";
                threadEndCodeIndex.setIndex(code.indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion, threadEndCodeIndex.getIndex() + 1));
                flag = false;
            }
            while(flag && code.charAt(tmpIndex) != '(') {
                if(code.charAt(tmpIndex) != ' ') {
                    toBeConsidered ="";
                    threadEndCodeIndex.setIndex(code.indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion, threadEndCodeIndex.getIndex() + 1));
                    break;
                }
                tmpIndex++;
            }
        }

        return toBeConsidered;
    }

    private void parseThreadStartCode(String code, StringWrapper extractedCode, IndexWrapper indexWrapper, int threadStartCodeIndex,
                                      Map<String, CodeSnippetElement> threadCodeSnippetMap,
                                      Map<String, List<CodeSnippetElement>> mappingToBeResolved,
                                      Map<String, List<CodeSnippetElement>> cloningToBeResolved,
                                      CodeSnippetElement codeSnippetElement,
                                      boolean isPython) {
        //threadStart block to be covered
        if(indexWrapper.getIndex() != threadStartCodeIndex) {
            String chunk = code.substring(indexWrapper.getIndex(), threadStartCodeIndex);
            extractedCode.concat(isPython ? chunk : chunk.trim());
        }
        indexWrapper.setIndex(threadStartCodeIndex);

        CodeContainer codeContainer = StringUtils.parseForCodeContainer(CodeToken.threadStart, code.substring(indexWrapper.getIndex()), indexWrapper);
        CodeSnippetElement childSnippet = getCodeSnippets(codeContainer.getCode(), threadCodeSnippetMap, mappingToBeResolved, cloningToBeResolved);
        String threadName = codeContainer.getArguments().get(0);
        threadCodeSnippetMap.put(threadName, childSnippet);
        codeSnippetElement.getNext().add(childSnippet);
        indexWrapper.setIndex(indexWrapper.getIndex() + threadStartCodeIndex);


        //Put the code into the cloned CodeSnippet
        List<CodeSnippetElement> clonedCodeSnippetElements = cloningToBeResolved.get(threadName);
        if(clonedCodeSnippetElements != null) {
            for(CodeSnippetElement clonedSnippet : clonedCodeSnippetElements) {
                clonedSnippet.setCode(childSnippet.getCode());
            }
        }
        //Resolve the mapping
        List<CodeSnippetElement> mappedCodeSnippets = mappingToBeResolved.get(threadName);
        if(mappedCodeSnippets != null) {
            for(CodeSnippetElement mappedCodeSnippet : mappedCodeSnippets) {
                childSnippet.getNext().add(mappedCodeSnippet);
            }
        }
    }

    private void parseThreadOnCompleteCode(String code, StringWrapper extractedCode, IndexWrapper indexWrapper,
                                           int threadEndCodeIndex,
                                           Map<String, CodeSnippetElement> threadCodeSnippetMap,
                                           Map<String, List<CodeSnippetElement>> mappingToBeResolved,
                                           Map<String, List<CodeSnippetElement>> cloningToBeResolved,
                                           boolean isPython) {
        //threadEnd block to be covered
        if(indexWrapper.getIndex() != threadEndCodeIndex) {
            String chunk = code.substring(indexWrapper.getIndex(), threadEndCodeIndex);
            extractedCode.concat(isPython ? chunk : chunk.trim());
        }
        indexWrapper.setIndex(threadEndCodeIndex);

        CodeContainer codeContainer = StringUtils.parseForCodeContainer(CodeToken.threadTriggerOnSomeThreadCompleteion,
                code.substring(indexWrapper.getIndex()), indexWrapper);
        indexWrapper.setIndex(indexWrapper.getIndex() + threadEndCodeIndex);
        CodeSnippetElement childSnippet = getCodeSnippets(codeContainer.getCode(), threadCodeSnippetMap, mappingToBeResolved, cloningToBeResolved);
        List<String> arguments = codeContainer.getArguments().subList(0, codeContainer.getArguments().size() - 1);
        int iterations = Integer.parseInt(codeContainer.getArguments().get(codeContainer.getArguments().size() -1));
        for(int iteration = 1; iteration <= iterations; iteration++) {
            CodeSnippetElement tempCodeSnippetElement = new CodeSnippetElement();
            tempCodeSnippetElement.setCode("");
            if(iteration == iterations) {
                tempCodeSnippetElement = childSnippet;
            }
            for(String argument : arguments) {
                connectDependentThread(threadCodeSnippetMap, mappingToBeResolved,
                        tempCodeSnippetElement, argument, iteration-1);

                if(iteration != iterations) {
                    CodeSnippetElement argumentCodeSnippetElementForNextIteration = new CodeSnippetElement();
                    cloneNewCodeSnippetWithOriginalCodeSnippetThatWillBeCreatedLater(
                            threadCodeSnippetMap, cloningToBeResolved, iteration, argument,
                            argumentCodeSnippetElementForNextIteration);
                    tempCodeSnippetElement.getNext().add(argumentCodeSnippetElementForNextIteration);
                }
            }
        }
    }

    private void parseThreadParallelismCycleCode(String code, StringWrapper extractedCode, IndexWrapper indexWrapper,
                                                  int threadParallelismCycleCodeIndex,
                                                  Map<String, CodeSnippetElement> threadCodeSnippetMap,
                                                  Map<String, List<CodeSnippetElement>> mappingToBeResolved,
                                                  Map<String, List<CodeSnippetElement>> cloningToBeResolved,
                                                  boolean isPython) {
        // threadParallelismCycle block: body runs after EVERY cycle (not just the last one)
        if(indexWrapper.getIndex() != threadParallelismCycleCodeIndex) {
            String chunk = code.substring(indexWrapper.getIndex(), threadParallelismCycleCodeIndex);
            extractedCode.concat(isPython ? chunk : chunk.trim());
        }
        indexWrapper.setIndex(threadParallelismCycleCodeIndex);

        CodeContainer codeContainer = StringUtils.parseForCodeContainer(CodeToken.threadParallelismCycle,
                code.substring(indexWrapper.getIndex()), indexWrapper);
        indexWrapper.setIndex(indexWrapper.getIndex() + threadParallelismCycleCodeIndex);
        List<String> arguments = codeContainer.getArguments().subList(0, codeContainer.getArguments().size() - 1);
        int iterations = Integer.parseInt(codeContainer.getArguments().get(codeContainer.getArguments().size() - 1));

        for(int iteration = 1; iteration <= iterations; iteration++) {
            // Each cycle's body gets its own CodeSnippetElement with the SAME code
            CodeSnippetElement cycleBodySnippet = getCodeSnippets(codeContainer.getCode(), threadCodeSnippetMap, mappingToBeResolved, cloningToBeResolved);

            // Connect all dependent threads for this iteration to the cycle body
            for(String argument : arguments) {
                connectDependentThread(threadCodeSnippetMap, mappingToBeResolved,
                        cycleBodySnippet, argument, iteration - 1);
            }

            // After the cycle body, spawn the next iteration's threads (except after the last cycle)
            if(iteration != iterations) {
                for(String argument : arguments) {
                    CodeSnippetElement nextIterThreadSnippet = new CodeSnippetElement();
                    cloneNewCodeSnippetWithOriginalCodeSnippetThatWillBeCreatedLater(
                            threadCodeSnippetMap, cloningToBeResolved, iteration, argument,
                            nextIterThreadSnippet);
                    cycleBodySnippet.getNext().add(nextIterThreadSnippet);
                }
            }
        }
    }

    /*
    * threadCodeSnippetMap is the map of threadId and the codeSnippet corresponding to it
    * mappingToBeResolved is the map between the thread and the list of CodeSnippet successor to the given thread.
    * For example: if the codeSnippet has to be triggered when thread t1 is done. But the code of t1 is written after the
    * threadOnEnd(t1) {codeSnippet}. So when t1's codeSnippet is recorded, we can associate the new codeSnippet with the code
    * of the threadOnEnd
    * cloningToBeResolved is the map between the thread and the list of clones of the thread. For example: a thread has to be
    * repeated for some iteration and the code of main thread has not been read by the parser. Then we add the clones of the code
    * in this map. When the required thread's code is taken by the parser, we can associate the cloned codeSnippets with the
    * requried codeSnippet
    * */

    public CodeSnippetElement getCodeSnippets(String code, Map<String, CodeSnippetElement> threadCodeSnippetMap,
                                               Map<String, List<CodeSnippetElement>> mappingToBeResolved, Map<String, List<CodeSnippetElement>> cloningToBeResolved) {
        CodeSnippetElement codeSnippetElement = new CodeSnippetElement();
        String extractedCode = "";
        int index = 0;
        boolean isPython = isPythonCode(code);
        int threadStartCodeIndex = code.indexOf(CodeToken.threadStart);
        int threadEndCodeIndex = code.indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion);
        int threadParallelismCycleIndex = code.indexOf(CodeToken.threadParallelismCycle);
        while(threadEndCodeIndex != -1 || threadStartCodeIndex != -1 || threadParallelismCycleIndex != -1) {
            IndexWrapper threadEndCodeIndexWrapper = new IndexWrapper(threadEndCodeIndex);
            IndexWrapper threadStartCodeIndexWrapper = new IndexWrapper(threadStartCodeIndex);
            IndexWrapper threadParallelismCycleIndexWrapper = new IndexWrapper(threadParallelismCycleIndex);
            String toBeConsidered = getToBeConsideredToken(code, threadStartCodeIndexWrapper, threadEndCodeIndexWrapper, threadParallelismCycleIndexWrapper);
            threadEndCodeIndex = threadEndCodeIndexWrapper.getIndex();
            threadStartCodeIndex = threadStartCodeIndexWrapper.getIndex();
            threadParallelismCycleIndex = threadParallelismCycleIndexWrapper.getIndex();
            if("".equals(toBeConsidered)) {
                continue;
            }

            if(CodeToken.threadStart.equalsIgnoreCase(toBeConsidered)) {
                StringWrapper extractedCodeWrapper = new StringWrapper(extractedCode);
                IndexWrapper indexWrapper = new IndexWrapper(index);
                parseThreadStartCode(code, extractedCodeWrapper, indexWrapper, threadStartCodeIndex, threadCodeSnippetMap,
                    mappingToBeResolved, cloningToBeResolved, codeSnippetElement, isPython);
                extractedCode = extractedCodeWrapper.getStr();
                index = indexWrapper.getIndex();
            } else if(CodeToken.threadParallelismCycle.equalsIgnoreCase(toBeConsidered)) {
                StringWrapper extractedCodeWrapper = new StringWrapper(extractedCode);
                IndexWrapper indexWrapper = new IndexWrapper(index);
                parseThreadParallelismCycleCode(code, extractedCodeWrapper, indexWrapper, threadParallelismCycleIndex,
                        threadCodeSnippetMap, mappingToBeResolved, cloningToBeResolved, isPython);
                extractedCode = extractedCodeWrapper.getStr();
                index = indexWrapper.getIndex();
            } else {
                StringWrapper extractedCodeWrapper = new StringWrapper(extractedCode);
                IndexWrapper indexWrapper = new IndexWrapper(index);
                parseThreadOnCompleteCode(code, extractedCodeWrapper, indexWrapper, threadEndCodeIndex, threadCodeSnippetMap,
                        mappingToBeResolved, cloningToBeResolved, isPython);
                extractedCode = extractedCodeWrapper.getStr();
                index = indexWrapper.getIndex();
            }
            if(index >= code.length() || code.substring(index).indexOf(CodeToken.threadStart) == -1) {
                threadStartCodeIndex = -1;
            } else {
                threadStartCodeIndex = code.substring(index).indexOf(CodeToken.threadStart) + index;
            }
            if(index >= code.length() || code.substring(index).indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion) == -1) {
                threadEndCodeIndex = -1;
            } else {
                threadEndCodeIndex = code.substring(index).indexOf(CodeToken.threadTriggerOnSomeThreadCompleteion) + index;
            }
            if(index >= code.length() || code.substring(index).indexOf(CodeToken.threadParallelismCycle) == -1) {
                threadParallelismCycleIndex = -1;
            } else {
                threadParallelismCycleIndex = code.substring(index).indexOf(CodeToken.threadParallelismCycle) + index;
            }
        }
        if(index < code.length()) {
            String tail = code.substring(index);
            extractedCode += isPython ? tail : tail.trim();
        }
        codeSnippetElement.setCode(extractedCode);
        return codeSnippetElement;
    }



    private void cloneNewCodeSnippetWithOriginalCodeSnippetThatWillBeCreatedLater(Map<String, CodeSnippetElement> threadCodeSnippetMap, Map<String, List<CodeSnippetElement>> cloningToBeResolved, int iteration, String dependentThread, CodeSnippetElement codeSnippetElement) {
        threadCodeSnippetMap.put(dependentThread + "_" + iteration, codeSnippetElement);
        if(threadCodeSnippetMap.get(dependentThread) != null) {
            codeSnippetElement.setCode(threadCodeSnippetMap.get(dependentThread).getCode());
            return;
        }
        List<CodeSnippetElement> toBeClonedSnippets = cloningToBeResolved.get(dependentThread);
        if(toBeClonedSnippets == null) {
            toBeClonedSnippets = new ArrayList<>();
        }
        toBeClonedSnippets.add(codeSnippetElement);
        cloningToBeResolved.put(dependentThread, toBeClonedSnippets);
    }

    private void connectDependentThread(Map<String, CodeSnippetElement> threadCodeSnippetMap, Map<String,
            List<CodeSnippetElement>> mappingToBeResolved, CodeSnippetElement childCodeSnippetElement, String dependentThread, int iteration) {
        dependentThread =  dependentThread + (iteration<1?"":"_"+iteration);
        if(threadCodeSnippetMap.get(dependentThread) != null) {
            threadCodeSnippetMap.get(dependentThread).getNext().add(childCodeSnippetElement);
        } else {
            List<CodeSnippetElement> list = mappingToBeResolved.get(dependentThread);
            if(list == null) {
                list = new ArrayList<>();
            }
            list.add(childCodeSnippetElement);
            mappingToBeResolved.put(dependentThread, list);
        }
    }


    /*
    * Converts all the functions into corresponding RuleEngineInput object and maintains hashMap between functionName
    * and corresponding RuleEngineInput object.
    * Return the code excluding the function code.
    * code: Given code in which functions have to picked and converted, and remaining code to be returned,
    * functionCallsRuleEngineInputMap: hashMap between the function-name and the corresponding RuleEngineInput object
    *
    * Heuristics:
    * 1. Get all the instances of function-declaration
    * 2. for each instance:
    *   2.1. extract the function from the given index
    *   2.2. call updateFunctionCallRuleEngineInputMap for the extracted-function-code
    * 3. return the remaining code
    * */
    public ExtractedCodeAndFunctionCode extractCodeWithoutAbstractCodeDeclaration(String code,
                                                                                  Map<String, RuleEngineInput> functionCallsRuleEngineInputMap, ActualDebugCodeCreator actualDebugCodeCreator)
            throws CompilationException {
        List<Integer> allInstaces = StringUtils.getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.functionDef, ' ');
        String extractedCode = "";
        int lastIndex = 0;
        int iteration = 0;

        while(iteration < allInstaces.size()) {
            int instanceIndex = allInstaces.get(iteration);
            extractedCode = extractedCode + code.substring(lastIndex, instanceIndex);
            IndexWrapper indexWrapper = new IndexWrapper(instanceIndex);
            updateFunctionCallRuleEngineInputMap(code, indexWrapper,
                    functionCallsRuleEngineInputMap, actualDebugCodeCreator);
            lastIndex = indexWrapper.getIndex();
            iteration++;
        }
        extractedCode = extractedCode + code.substring(lastIndex, code.length());
        ExtractedCodeAndFunctionCode extractedCodeWithoutAbstractCodeDeclaration =
                new ExtractedCodeAndFunctionCode();
        extractedCodeWithoutAbstractCodeDeclaration.setExtractedCode(extractedCode);
        extractedCodeWithoutAbstractCodeDeclaration.setFunctionCode(actualDebugCodeCreator.getDebugCode());
        return extractedCodeWithoutAbstractCodeDeclaration;
    }

    /*
    * Converts the function code to corresponding RuleEngineInput object.
    * code: The whole code submitted
    * indexWrapper1: index from where function starts
    * functionCallsRuleEngineInputMap: hashMap between functionName and the RuleEngineInput object corresponding to it
    *
    * Heuristic:
    * 1. Get the information(codeContainer) of the given function
    * 2. convert the function-code using the codeConverter.interpret method
    * */
    private void updateFunctionCallRuleEngineInputMap(String code, IndexWrapper indexWrapper1,
                                                      Map<String, RuleEngineInput> functionCallsRuleEngineInputMap,
                                                      ActualDebugCodeCreator debugCodeCreator)
            throws CompilationException{


        IndexWrapper codeContainerIndex = new IndexWrapper(0);
        CodeContainer functionCodeInformation = StringUtils.parseForCodeContainer(CodeToken.functionDef,
                code.substring(indexWrapper1.getIndex()), codeContainerIndex);

        CodeConverter codeConverter = getNewCodeConverter(new ArrayList<>());
        String functionCode = functionCodeInformation.getCode();
        RuleEngineInput ruleEngineInput = functionCallsRuleEngineInputMap.get(functionCode);
        List<String> arguments = functionCodeInformation.getArguments();
        String functionName = functionCodeInformation.getPlaceHolder();
        if(ruleEngineInput == null) {
            ruleEngineInput = new RuleEngineInput();
        }
        List<String> variableScope = new LinkedList<>();
        variableScope.add("func_" + functionCodeInformation.getPlaceHolder());

        VariableInitLogicConverter variableInitLogicConverter = new VariableInitLogicConverter();
        
        Map<Integer, RuleEngineInputUnits> variableFrameMap = new HashMap<>();
        int counter = 0;
        for(String argumentCode : arguments) {
            RuleEngineInputUnits ruleEngineInputs;
            if(!argumentCode.contains("var"))
            {
                MethodDataTypeAgnosticArg methodDataTypeAgnosticArg = new MethodDataTypeAgnosticArg();
                methodDataTypeAgnosticArg.setId(UUID.randomUUID().toString());
                methodDataTypeAgnosticArg.setName(argumentCode);
                methodDataTypeAgnosticArg.setFrameCount(counter);
                ruleEngineInput.getMethodDataTypeAgnosticArgs().add(methodDataTypeAgnosticArg);
                ruleEngineInputs = methodDataTypeAgnosticArg;
                codeConverter.setMethodDataTypeAgnosticArgMap(methodDataTypeAgnosticArg, variableScope.size() > 0 ? variableScope.get(variableScope.size() - 1) : "");
            }
            else {
                ruleEngineInputs = variableInitLogicConverter.convertCode(argumentCode, ruleEngineInput, codeConverter, variableScope, new NoConcatImpl(), null, null);
                if (ruleEngineInputs instanceof Variable) {
                    ((Variable) ruleEngineInputs).setFrameCount(counter);
                }
                if (ruleEngineInputs instanceof Array) {
                    ((Array) ruleEngineInputs).setFrameCount(counter);
                }
            }
            variableFrameMap.put(counter, ruleEngineInputs);
            counter++;
        }
        final Integer[] counterId = {counter};
        debugCodeCreator.concat(CodeToken.functionDef + " " + functionName + "("
                + getCommaSeperatedArgs(arguments) + ") {");
        debugCodeCreator.addIndentation();
        debugCodeCreator.nextLine();
        List<Command> functionCommandList = codeConverter.interpret(functionCode, ruleEngineInput, variableScope, debugCodeCreator, variableFrameMap, counterId);
        RuleEngineUtils.addFunctionCall(ruleEngineInput, functionName, arguments, codeConverter, functionCommandList, variableScope, variableFrameMap);
        functionCallsRuleEngineInputMap.put(functionName, ruleEngineInput);
        debugCodeCreator.decrementIndentation();
        debugCodeCreator.concat("}");
        debugCodeCreator.nextLine();

        indexWrapper1.setIndex(indexWrapper1.getIndex() + codeContainerIndex.getIndex());

    }

    private String getCommaSeperatedArgs(final List<String> arguments) {
        String str = "";
        int size = arguments.size();
        for(int i=0; i<size-1;i++) {
            str += (arguments.get(i) + ",");
        }
        str += arguments.get(size - 1);
        return str;
    }

}

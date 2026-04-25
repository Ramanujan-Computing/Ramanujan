package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.diagram.Diagram;
import in.ramanujan.developer.console.model.pojo.CodeRunAsyncResponse;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.developer.console.pojo.ApiResponse;
import in.ramanujan.developer.console.utils.PackageBuildHelper;
import in.ramanujan.translation.codeConverter.pojo.VariableMappingLite;
import in.ramanujan.translation.codeConverter.pojo.ArrayMappingLite;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class ExecutorImpl implements Operation {
    // Static maps to store variables and arrays for querying after execution
    public static final Map<String, Object> variableStore = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<String, Map<String, Object>> arrayStore = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void execute(List<String> args) throws IOException {
            runCode(args);
    }

    private static void runCode(List<String> args) throws JsonProcessingException {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(500_000, java.util.concurrent.TimeUnit.MILLISECONDS);
        OkHttpClient httpClient = new OkHttpClient(builder);
        String json = new ObjectMapper().writeValueAsString(createJson(args));
        RequestBody requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
        Request request = new Request.Builder().url("http://127.0.0.1:8888/run?debug=false").post(requestBody).build();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Response response = httpClient.newCall(request).execute();
            if(response.code() == 200) {
                ApiResponse responseJson = objectMapper.readValue(response.body().string(), ApiResponse.class);
                CodeRunAsyncResponse codeRunAsyncResponse = objectMapper.convertValue(responseJson.getData(),
                        CodeRunAsyncResponse.class);
                String taskId = codeRunAsyncResponse.getAsyncId();
                System.out.println(taskId);
                Diagram diagram = codeRunAsyncResponse.getDiagram();
                System.out.println(diagram);
                request = new Request.Builder().url("http://127.0.0.1:8888/status?uuid=" + taskId).build();
                while(true) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        System.out.println("Thread interrupted while waiting for task completion.");
                    }
                    try {
                        response = httpClient.newCall(request).execute();
                    } catch (IOException ex) {
                        continue;
                    }
                    if(response.code() != 200) {
                        response.close();
                        continue;
                    }
                    ApiResponse apiResponse = objectMapper.readValue(response.body().string(), ApiResponse.class);
                    if("200 OK".equalsIgnoreCase(apiResponse.getStatus())) {
                        Map<String, Object> asyncTask = (Map<String, Object>) apiResponse.getData();
                        if("SUCCESS".equalsIgnoreCase((String) asyncTask.get("taskStatus")) || "FAILED".equalsIgnoreCase((String) asyncTask.get("taskStatus"))) {
                            System.out.println(asyncTask);
                            // Extract and store variable/array values for later querying
                            Object resultObj = asyncTask.get("result");
                            if (resultObj instanceof Map) {
                                Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                                Object variablesObj = resultMap.get("variables");
                                Object arraysObj = resultMap.get("arrays");
                                if (variablesObj instanceof List) {
                                    List<?> variables = (List<?>) variablesObj;
                                    for (Object varObj : variables) {
                                        // Use VariableMappingLite for type safety
                                        VariableMappingLite var = new ObjectMapper().convertValue(varObj, VariableMappingLite.class);
                                        variableStore.put(var.getVariableName(), var.getObject());
                                    }
                                }
                                if (arraysObj instanceof List) {
                                    List<?> arrays = (List<?>) arraysObj;
                                    for (Object arrObj : arrays) {
                                        // Use ArrayMappingLite for type safety
                                        ArrayMappingLite arr = new ObjectMapper().convertValue(arrObj, ArrayMappingLite.class);
                                        String name = arr.getArrayId();
                                        if(name.contains("func")) {
                                            continue;
                                        }
                                        name = name.split("_name_")[1];
                                        String indexStr = arr.getIndexStr();
                                        Object value = arr.getObject();
                                        Map<String, Object> arrMap = arrayStore.getOrDefault(name, new java.util.HashMap<>());
                                        arrMap.put(indexStr, value);
                                        arrayStore.put(name, arrMap);
                                    }
                                }
                            }
                            // Start interactive console for querying variables/arrays
                            startQueryConsole();
                            break;
                        }
                    }
                }
            } else {
                runCode(args); // Retry if the response is not OK
            }
        } catch (IOException e) {
            runCode(args);
            //System.out.println("faced some network issue");
        }
    }

    /**
     * Exposes the interactive console for querying variable and array values after execution.
     */
    public static void startQueryConsole() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.println("\n--- Query Console ---");
        System.out.println("Commands: 'var <name>', 'arr <name> <index>', 'dump <name> [file]', 'exit'");
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine();
            if (line == null) break;
            line = line.trim();
            if (line.equalsIgnoreCase("exit")) break;
            if (line.startsWith("var ")) {
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    Object val = variableStore.get(parts[1]);
                    if (val != null) {
                        System.out.println(parts[1] + " = " + val);
                    } else {
                        System.out.println("Variable not found.");
                    }
                } else {
                    System.out.println("Usage: var <variableName>");
                }
            } else if (line.startsWith("dump ")) {
                // dump <arrayName> [outputFile] — outputs entire array as CSV
                String[] parts = line.split(" ");
                String arrName = parts.length >= 2 ? parts[1] : null;
                String outFile = parts.length >= 3 ? parts[2] : null;
                if (arrName == null) {
                    System.out.println("Usage: dump <arrayName> [outputFile]");
                    continue;
                }
                Map<String, Object> arr = arrayStore.get(arrName);
                if (arr == null || arr.isEmpty()) {
                    System.out.println("Array not found or empty: " + arrName);
                    continue;
                }
                // Determine dimensions from keys (support both "idx" for 1D and "row_col" for 2D)
                boolean is1D = true;
                int maxRow = 0, maxCol = 0;
                for (String key : arr.keySet()) {
                    String[] dims = key.split("_");
                    if (dims.length >= 2) {
                        is1D = false;
                        maxRow = Math.max(maxRow, Integer.parseInt(dims[0]));
                        maxCol = Math.max(maxCol, Integer.parseInt(dims[1]));
                    } else if (dims.length == 1) {
                        maxRow = Math.max(maxRow, Integer.parseInt(dims[0]));
                    }
                }
                StringBuilder csv = new StringBuilder();
                if (is1D) {
                    // 1D array: output as single row, comma-separated
                    for (int i = 0; i <= maxRow; i++) {
                        if (i > 0) csv.append(',');
                        Object val = arr.get(String.valueOf(i));
                        csv.append(val != null ? val.toString() : "0.0");
                    }
                    csv.append('\n');
                } else {
                    // 2D array: output as multi-row CSV
                    for (int r = 0; r <= maxRow; r++) {
                        for (int c = 0; c <= maxCol; c++) {
                            if (c > 0) csv.append(',');
                            Object val = arr.get(r + "_" + c);
                            csv.append(val != null ? val.toString() : "0.0");
                        }
                        csv.append('\n');
                    }
                }
                String dims = is1D ? String.valueOf(maxRow+1) : (maxRow+1) + "x" + (maxCol+1);
                if (outFile != null) {
                    try {
                        java.nio.file.Files.write(java.nio.file.Paths.get(outFile),
                            csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        System.out.println("Dumped " + arrName + " (" + dims + ") to " + outFile);
                    } catch (Exception e) {
                        System.out.println("Error writing file: " + e.getMessage());
                    }
                } else {
                    System.out.print(csv.toString());
                }
            } else if (line.startsWith("arr ")) {
                String[] parts = line.split(" ");
                if (parts.length == 3) {
                    Map<String, Object> arr = arrayStore.get(parts[1]);
                    if (arr != null && arr.containsKey(parts[2])) {
                        System.out.println(parts[1] + "[" + parts[2] + "] = " + arr.get(parts[2]));
                    } else {
                        System.out.println("Array or index not found.");
                    }
                } else {
                    System.out.println("Usage: arr <arrayName> <index>");
                }
            } else {
                System.out.println("Unknown command. Use 'var <name>', 'arr <name> <index>', 'dump <name> [file]', or 'exit'.");
            }
        }
    }

    /**
     * Set the variableStore and arrayStore maps for the interactive console.
     */
    public static void setStores(Map<String, Object> variableMap, Map<String, Map<String, Object>> arrayMap) {
        variableStore.clear();
        if (variableMap != null) variableStore.putAll(variableMap);
        arrayStore.clear();
        if (arrayMap != null) arrayStore.putAll(arrayMap);
    }

    public static CodeRunRequest createJson(List<String> args) throws JsonProcessingException {
        String codeString = PackageBuildHelper.readFile(args.get(0));
        CodeRunRequest codeRunRequest = new CodeRunRequest();
        codeRunRequest.setCode(codeString);
        codeRunRequest.setCsvInformationList(new ArrayList<>());
        if(args.size() > 0) {
            for(int iter = 1; iter < args.size(); iter++) {
                long csvReadStart = System.currentTimeMillis();
                String csvPath = args.get(iter);
                System.out.println("[createJson] CSV " + iter + "/" + (args.size()-1) + ": " + csvPath);
                System.out.flush();
                CsvInformation csvInformation = new CsvInformation();
                csvInformation.setFileName(csvPath);

                // Fast path: if a .bin file exists (resolving any symlinks), skip reading
                // the full CSV text — just read the first line to get column count and
                // compute row count from the binary file size.
                java.io.File binFile = resolveBinFile(csvPath);
                if (binFile != null && binFile.exists() && binFile.length() > 0) {
                    try {
                        long numFloats = binFile.length() / 4;
                        // Read first line AND peek at second line.
                        // If second line exists → multi-row format, numCols from first line is correct.
                        // If only one line → flat format, first line has ALL values; pick a smart numCols.
                        int[] dims = inferDims(csvPath, numFloats);
                        int numRows = dims[0];
                        int numCols = dims[1];
                        // Synthesise a minimal stub CSV so generateCsvDeclPythonCode gets correct dimensions.
                        // buildDimStub: first row has correct column count; remaining rows are just "0\n".
                        // This is ~24 KB for a 9216x3072 matrix vs ~56 MB for buildZeroGrid — 2000x smaller.
                        csvInformation.setData(buildDimStub(numRows, numCols));
                        System.err.println("[createJson]   Binary fast-path: " + numRows + "x" + numCols
                                + " (" + (binFile.length()/1024/1024) + " MB bin) in "
                                + (System.currentTimeMillis() - csvReadStart) + "ms");
                        codeRunRequest.getCsvInformationList().add(csvInformation);
                        continue;
                    } catch (Exception e) {
                        System.err.println("[createJson]   Binary fast-path failed (" + e.getMessage() + "), falling back to full CSV read");
                    }
                }

                // Fallback: read the full CSV text
                String csvData = PackageBuildHelper.readFileWithNewLine(csvPath);
                csvInformation.setData(csvData);
                codeRunRequest.getCsvInformationList().add(csvInformation);
                System.out.println("[createJson]   Read " + (csvData == null ? "null" : (csvData.length()/1024) + " KB") + " in " + (System.currentTimeMillis() - csvReadStart) + "ms");
                System.out.flush();
            }
        }

        return codeRunRequest;
    }

    /**
     * Resolves a CSV path to its sibling .bin file, following symlinks to find the real .bin.
     */
    private static java.io.File resolveBinFile(String csvPath) {
        if (csvPath == null || !csvPath.endsWith(".csv")) return null;
        // 1. Try next to the CSV as-is
        java.io.File binFile = new java.io.File(csvPath.substring(0, csvPath.length() - 4) + ".bin");
        if (binFile.exists() && binFile.length() > 0) return binFile;
        // 2. Resolve symlink and try next to the real file
        try {
            java.nio.file.Path real = java.nio.file.Paths.get(csvPath).toRealPath();
            binFile = new java.io.File(real.toString().substring(0, real.toString().length() - 4) + ".bin");
            if (binFile.exists() && binFile.length() > 0) return binFile;
        } catch (Exception ignored) {}
        return null;
    }

    /** Reads only the first non-empty line of a (possibly symlinked) file. */
    private static String readFirstLine(String path) throws java.io.IOException {
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) return line;
            }
        }
        return null;
    }

    /**
     * Infers [numRows, numCols] for a CSV backed by a binary file of numFloats elements.
     * Handles two CSV formats:
     *   (a) Multi-row: each row has numCols values  → read first line for numCols, derive numRows.
     *   (b) Flat: all values on one line            → first line has numFloats values.
     *             In this case pick the best numCols from a set of common divisors so that the
     *             resulting buildDimStub is compact (only the first row is "verbose").
     */
    private static int[] inferDims(String csvPath, long numFloats) throws java.io.IOException {
        // Read first line with a hard cap (2MB): if no newline found within cap → flat format.
        final int CAP = 2 * 1024 * 1024;  // 2 MB
        char[] buf = new char[CAP];
        int read = 0;
        boolean hitNewline = false;
        int newlinePos = -1;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(csvPath)), 65536)) {
            read = br.read(buf, 0, CAP);
            if (read > 0) {
                for (int i = 0; i < read; i++) {
                    if (buf[i] == '\n') { hitNewline = true; newlinePos = i; break; }
                }
            }
        }
        if (!hitNewline) {
            // Flat format: entire matrix on one line (or line > 2 MB = effectively flat).
            // Pick a compact numCols: largest divisor of numFloats from common hidden-dim sizes.
            int[] candidates = {8192, 4096, 3072, 2048, 1536, 1024, 768, 512, 256, 128, 64, 32};
            for (int c : candidates) {
                if (numFloats % c == 0 && numFloats / c <= Integer.MAX_VALUE) {
                    return new int[]{(int)(numFloats / c), c};
                }
            }
            return new int[]{(int)numFloats, 1};
        }
        // Multi-row format: count commas in first line to determine numCols.
        int commas = 0;
        for (int i = 0; i < newlinePos; i++) if (buf[i] == ',') commas++;
        int numCols = commas + 1;
        int numRows = (numCols > 0) ? (int)(numFloats / numCols) : 1;
        return new int[]{numRows, numCols};
    }

    private static int countCommas(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ',') count++;
        return count;
    }

    /** Builds a single-row stub CSV of zeros (for 1-D arrays). */
    private static String buildZeroRow(int cols) {
        StringBuilder sb = new StringBuilder(cols * 4);
        for (int i = 0; i < cols; i++) { if (i > 0) sb.append(','); sb.append('0'); }
        sb.append('\n');
        return sb.toString();
    }

    /** Builds a multi-row stub CSV of zeros (for 2-D arrays). */
    private static String buildZeroGrid(int rows, int cols) {
        String row = buildZeroRow(cols);
        StringBuilder sb = new StringBuilder(rows * row.length());
        for (int i = 0; i < rows; i++) sb.append(row);
        return sb.toString();
    }

    /**
     * Builds a minimal dimension stub for binary-fast-path use.
     * First row has the correct column count (for generateCsvDeclPythonCode to count commas).
     * Remaining rows are just "0\n" — enough to give the correct row count when \n is counted,
     * but without the per-cell overhead of buildZeroGrid.
     * Size: O(cols + rows) instead of O(rows * cols).
     * Example: 9216x3072 → ~24 KB vs ~56 MB for buildZeroGrid.
     */
    private static String buildDimStub(int rows, int cols) {
        if (rows <= 1) return buildZeroRow(cols);
        StringBuilder sb = new StringBuilder(cols * 2 + rows * 2);
        // First row: full column count
        for (int c = 0; c < cols; c++) { if (c > 0) sb.append(','); sb.append('0'); }
        sb.append('\n');
        // Remaining rows: single zero per row (just enough newlines for row count)
        for (int r = 1; r < rows; r++) sb.append("0\n");
        return sb.toString();
    }
}

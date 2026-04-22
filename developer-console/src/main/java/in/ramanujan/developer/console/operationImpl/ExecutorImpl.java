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
                System.out.println("[createJson] Reading CSV " + iter + "/" + (args.size()-1) + ": " + args.get(iter));
                System.out.flush();
                CsvInformation csvInformation = new CsvInformation();
                String csvData = PackageBuildHelper.readFileWithNewLine(args.get(iter));
                csvInformation.setData(csvData);
                csvInformation.setFileName(args.get(iter));
                codeRunRequest.getCsvInformationList().add(csvInformation);
                System.out.println("[createJson]   Read " + (csvData == null ? "null" : (csvData.length()/1024) + " KB") + " in " + (System.currentTimeMillis() - csvReadStart) + "ms");
                System.out.flush();
            }
        }

        return codeRunRequest;
    }
}

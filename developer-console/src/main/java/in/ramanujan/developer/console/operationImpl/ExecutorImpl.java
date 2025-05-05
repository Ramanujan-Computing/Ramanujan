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
import in.ramanujan.middleware.base.pojo.VariableMappingLite;
import in.ramanujan.middleware.base.pojo.ArrayMappingLite;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutorImpl implements Operation {
    // Static maps to store variables and arrays for querying after execution
    public static final Map<String, Object> variableStore = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<String, Map<String, Object>> arrayStore = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void execute(List<String> args) {
        try {
            OkHttpClient httpClient = new OkHttpClient();
            String json = new ObjectMapper().writeValueAsString(createJson(args));
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
            Request request = new Request.Builder().url("http://localhost:8888/run?debug=false").post(requestBody).build();
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
                    request = new Request.Builder().url("http://localhost:8888/status?uuid=" + taskId).build();
                    while(true) {
                        response = httpClient.newCall(request).execute();
                        if(response.code() != 200) {
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
                                            String indexStr = arr.getIndexStr();
                                            Object value = arr.getObject();
                                            Map<String, Object> arrMap = arrayStore.getOrDefault(name, new java.util.HashMap<>());
                                            arrMap.put(indexStr, value);
                                            arrayStore.put(name, arrMap);
                                        }
                                    }
                                }
                                // Start interactive console for querying variables/arrays
                                java.util.Scanner scanner = new java.util.Scanner(System.in);
                                System.out.println("\n--- Query Console ---");
                                System.out.println("Type 'var <variableName>' or 'arr <arrayName> <index>' to query. Type 'exit' to quit.");
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
                                        System.out.println("Unknown command. Use 'var <variableName>' or 'arr <arrayName> <index>' or 'exit'.");
                                    }
                                }
                                break;
                            }
                        }
                    }
                } else {
                    System.out.println(response.body().string());
                }
            } catch (IOException e) {
                System.out.println("faced some network issue");
            }
        } catch (JsonProcessingException e) {
            System.out.println("Console error");
        }
    }

    public static CodeRunRequest createJson(List<String> args) throws JsonProcessingException {
        String codeString = PackageBuildHelper.readFile(args.get(0));
        CodeRunRequest codeRunRequest = new CodeRunRequest();
        codeRunRequest.setCode(codeString);
        codeRunRequest.setCsvInformationList(new ArrayList<>());
        if(args.size() > 0) {
            for(int iter = 1; iter < args.size(); iter++) {
                CsvInformation csvInformation = new CsvInformation();
                csvInformation.setData(PackageBuildHelper.readFileWithNewLine(args.get(iter)));
                csvInformation.setFileName(args.get(iter));
                codeRunRequest.getCsvInformationList().add(csvInformation);
            }
        }

        return codeRunRequest;
    }
}

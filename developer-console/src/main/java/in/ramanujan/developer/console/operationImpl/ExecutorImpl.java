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
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutorImpl implements Operation {
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
                            Map asyncTask = (Map) apiResponse.getData();
                            if("SUCCESS".equalsIgnoreCase((String) asyncTask.get("taskStatus")) || "FAILED".equalsIgnoreCase((String) asyncTask.get("taskStatus"))) {
                                System.out.println(asyncTask);
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

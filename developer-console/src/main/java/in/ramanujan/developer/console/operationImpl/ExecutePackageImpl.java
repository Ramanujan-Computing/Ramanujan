package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.diagram.Diagram;
import in.ramanujan.developer.console.model.pojo.CodeRunAsyncResponse;
import in.ramanujan.developer.console.model.pojo.PackageRunInput;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.developer.console.pojo.ApiResponse;
import in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty.Dependency;
import in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty.PackageBuilder;
import in.ramanujan.developer.console.utils.PackageBuildHelper;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
* Required arguments: executePackage packageDirectory
* packageDirectory will have all the files
* There will be build.json which will contain external dependency that is required.
* ~/.ramanujan/dependencies/ to have the dependencies
* If dependency is there in the directory, it will be included from that directory. If directory is not there, it will be
* downloaded (this feasibility has to be implemented)
* */
public class ExecutePackageImpl implements Operation {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(List<String> args) {
        String packageDirectory = args.get(0);
        PackageBuilder packageBuilder = PackageBuildHelper.getPackageBuilder(packageDirectory);
        String mainClass = packageBuilder.getMainClass();
        List<Dependency> dependencies = packageBuilder.getDependencies();
        PackageRunInput packageRunInput = new PackageRunInput();
        PackageBuildHelper.addMainClassCode(packageDirectory, mainClass, packageRunInput);
        PackageBuildHelper.addOtherFilesInDirectory(packageDirectory, mainClass, packageRunInput);
        PackageBuildHelper.addDependencies(dependencies, packageRunInput);
        addCsvInformation(packageRunInput, args);

        sendToBackend(packageRunInput);
    }

    private void addCsvInformation(PackageRunInput packageRunInput, List<String> args) {
        packageRunInput.setCsvInformationList(new ArrayList<>());
        if(args.size() > 0) {
            for(int iter = 1; iter < args.size(); iter++) {
                CsvInformation csvInformation = new CsvInformation();
                csvInformation.setData(PackageBuildHelper.readFileWithNewLine(args.get(iter)));
                csvInformation.setFileName(args.get(iter));
                packageRunInput.getCsvInformationList().add(csvInformation);
            }
        }
    }

    private void sendToBackend(PackageRunInput packageRunInput) {
        try {
            OkHttpClient httpClient = new OkHttpClient();
            String json = objectMapper.writeValueAsString(packageRunInput);
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
            Request request = new Request.Builder().url("http://localhost:8888/run/package").post(requestBody).build();
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
}

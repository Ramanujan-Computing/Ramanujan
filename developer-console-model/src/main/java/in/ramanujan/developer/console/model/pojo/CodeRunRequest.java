package in.ramanujan.developer.console.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeRunRequest {
    private String code;
    private String entryPoint;
    private List<CsvInformation> csvInformationList;
    private Map<String, String> files;
    private Map<String, String> pythonFiles;

    @com.fasterxml.jackson.annotation.JsonProperty("entrypoint")
    public void setEntrypointAlias(String entrypoint) {
        if (this.entryPoint == null) {
            this.entryPoint = entrypoint;
        }
    }

    @com.fasterxml.jackson.annotation.JsonProperty("entry_point")
    public void setEntryPointSnakeAlias(String entryPoint) {
        if (this.entryPoint == null) {
            this.entryPoint = entryPoint;
        }
    }

    @com.fasterxml.jackson.annotation.JsonProperty("mainFile")
    public void setMainFileAlias(String mainFile) {
        if (this.entryPoint == null) {
            this.entryPoint = mainFile;
        }
    }

    @com.fasterxml.jackson.annotation.JsonProperty("main_file")
    public void setMainFileSnakeAlias(String mainFile) {
        if (this.entryPoint == null) {
            this.entryPoint = mainFile;
        }
    }

    public Map<String, String> getAllFiles() {
        Map<String, String> all = new HashMap<>();
        if (files != null) {
            all.putAll(files);
        }
        if (pythonFiles != null) {
            all.putAll(pythonFiles);
        }
        return all;
    }
}

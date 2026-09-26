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
    private List<CsvInformation> csvInformationList;
    private Map<String, String> files;
    private Map<String, String> pythonFiles;

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

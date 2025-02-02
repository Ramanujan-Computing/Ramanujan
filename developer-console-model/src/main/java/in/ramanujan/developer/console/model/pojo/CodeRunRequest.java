package in.ramanujan.developer.console.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeRunRequest {
    private String code;
    private List<CsvInformation> csvInformationList;
}

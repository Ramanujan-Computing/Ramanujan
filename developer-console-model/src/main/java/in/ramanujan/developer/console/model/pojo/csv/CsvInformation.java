package in.ramanujan.developer.console.model.pojo.csv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CsvInformation {
    private String fileName;
    private String data;
}

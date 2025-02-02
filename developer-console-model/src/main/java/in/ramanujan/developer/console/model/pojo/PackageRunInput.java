package in.ramanujan.developer.console.model.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageRunInput extends CodeRunRequest{
    private String mainCode;
    private Map<String, String> headerCodes;

    @Override
    public String getCode() {
        String code = "";
        if(getHeaderCodes() != null) {
            for(String fileName : headerCodes.keySet()) {
                code += headerCodes.get(fileName);
            }
        }
        code += mainCode;

        return code;
    }

}

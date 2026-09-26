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
        if (mainCode != null && isPython(mainCode)) {
            return mainCode;
        }
        String code = "";
        if(getHeaderCodes() != null) {
            for(String fileName : headerCodes.keySet()) {
                code += headerCodes.get(fileName);
            }
        }
        code += (mainCode != null ? mainCode : "");

        return code;
    }

    private boolean isPython(String code) {
        if (code == null) return false;
        String clean = code.replaceAll("(?m)#[^\n]*", "").trim();
        return (!clean.contains("{") && !clean.contains("}")) || clean.contains("def ") || clean.contains("import ");
    }

    @Override
    public Map<String, String> getAllFiles() {
        Map<String, String> all = super.getAllFiles();
        if (headerCodes != null) {
            all.putAll(headerCodes);
        }
        return all;
    }

}

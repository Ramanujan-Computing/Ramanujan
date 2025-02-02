package in.ramanujan.developer.console.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DebugResult {
    private String commonCode;
    private String code;
    private List<UserReadableDebugPoint> userReadableDebugPoint;
    private List<String> nextDagElementIds;

    @JsonIgnore
    private int debugPointer = 0;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserReadableDebugPoint {
        private String commandId;
        private Map<String, String> beforeValue = new HashMap();
        private Map<String, String> afterValue = new HashMap();
        private Map<String, String> functionArgCallArgMap = new HashMap();
        private Boolean conditionVal;
        private Integer codePtr;
    }
}

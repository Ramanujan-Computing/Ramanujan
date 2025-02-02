package in.ramanujan.debugger;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class UserReadableDebugPoint {
    private String commandId;
    private Map<String, String> beforeValue = new HashMap<>();
    private Map<String, String> afterValue = new HashMap<>();
    private Map<String, String> functionArgCallArgMap = new HashMap<>();
    Boolean conditionVal;
    private Integer codePtr;
}

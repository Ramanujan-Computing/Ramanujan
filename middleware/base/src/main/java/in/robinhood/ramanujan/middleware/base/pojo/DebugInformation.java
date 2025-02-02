package in.robinhood.ramanujan.middleware.base.pojo;

import in.robinhood.ramanujan.debugger.UserReadableDebugPoint;
import lombok.Data;

import java.util.List;

@Data
public class DebugInformation {
    private String commonCode;
    private String code;
    private List<UserReadableDebugPoint> userReadableDebugPoint;
    private List<String> nextDagElementIds;
}

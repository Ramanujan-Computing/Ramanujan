package in.ramanujan.devices.common.pojo;

import in.ramanujan.debugger.UserReadableDebugPoint;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ResultSubmitPayload {
    private String uuid;
    private String hostId;
    private Map<String, Object> data;

    @Data
    @AllArgsConstructor
    public static class UserReadableDebugPoints {
        private List<UserReadableDebugPoint> debugData;
    }
}

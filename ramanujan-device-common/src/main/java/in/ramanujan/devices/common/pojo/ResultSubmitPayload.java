package in.ramanujan.devices.common.pojo;

import in.ramanujan.debugger.UserReadableDebugPoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultSubmitPayload {
    private String uuid;
    private String hostId;
    private Map<String, Object> data;
    private String resultObject;
    private Long resultSize;
    private String resultMd5;

    public ResultSubmitPayload(String uuid, String hostId, Map<String, Object> data) {
        this.uuid = uuid;
        this.hostId = hostId;
        this.data = data;
    }

    @Data
    @AllArgsConstructor
    public static class UserReadableDebugPoints {
        private List<UserReadableDebugPoint> debugData;
    }
}

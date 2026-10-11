package in.ramanujan.pojo.storage;

import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@ToString(exclude = "uploadUrl")
public class ResultUploadTarget {
    private String uploadUrl;
    private String objectName;
    private long expiresAt;
    private Map<String, String> headers;
}

package in.ramanujan.translation.codeConverter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.debugger.UserReadableDebugPoint;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserReadableDebugPoints {
    private List<UserReadableDebugPoint> debugData;
}

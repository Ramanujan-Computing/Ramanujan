package in.robinhood.ramanujan.middleware.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.robinhood.ramanujan.debugger.UserReadableDebugPoint;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserReadableDebugPoints {
    private List<UserReadableDebugPoint> debugData;
}

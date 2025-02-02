package in.robinhood.ramanujan.middleware.base.pojo;

import in.robinhood.ramanujan.middleware.base.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class DeviceExecStatus {
    Status status;
    Map<String, Object> data;
}

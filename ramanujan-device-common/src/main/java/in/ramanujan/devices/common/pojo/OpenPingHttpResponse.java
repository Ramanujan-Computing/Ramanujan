package in.ramanujan.devices.common.pojo;

import lombok.Data;

@Data
public class OpenPingHttpResponse {
    private String status;
    private OpenPingApiResponse data;
}

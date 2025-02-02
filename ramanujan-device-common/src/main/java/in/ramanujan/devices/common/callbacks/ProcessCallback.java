package in.ramanujan.devices.common.callbacks;

import in.ramanujan.devices.common.pojo.OpenPingApiResponse;

import java.util.Map;

public interface ProcessCallback {
    public void sendResults(Map<String, Object> results, OpenPingApiResponse openPingApiResponse);
}

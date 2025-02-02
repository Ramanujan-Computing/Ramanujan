package in.ramanujan.devices.common;

import com.squareup.okhttp.*;
import in.ramanujan.devices.common.Credentials.Credentials;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static in.ramanujan.devices.common.OrchestratorApiCallHelper.*;

public class HeartbeatPinger {
    private static OkHttpClient okHttpClient;

    private static String heartBeatUri = "/pings/heartbeat";

    public static void pingHeartbeat(String uuid, Credentials credentials) {
        Request request = new Request.Builder()
                .post(RequestBody.create(JSON, ""))
                .url(host + heartBeatUri + "?uuid=" + uuid)
                .build();

        try {
//            System.out.println("pinging");
            getOkHttpClient().newCall(request).execute().body().close();
//            System.out.println("pinged");
        } catch (Exception e) {

        }
    }
}

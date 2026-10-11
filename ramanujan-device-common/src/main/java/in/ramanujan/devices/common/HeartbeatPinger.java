package in.ramanujan.devices.common;

import com.squareup.okhttp.*;
import in.ramanujan.devices.common.Credentials.Credentials;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static in.ramanujan.devices.common.OrchestratorApiCallHelper.*;

public class HeartbeatPinger {
    private static OkHttpClient okHttpClient;

    private static String heartBeatUri = "/pings/heartbeat";
    private static final Logger logger = Logger.getLogger(HeartbeatPinger.class.getName());

    public static void pingHeartbeat(String uuid, String asyncId, Credentials credentials) {
        Request request = new Request.Builder()
                .post(RequestBody.create(JSON, ""))
                .url(host + heartBeatUri + "?uuid=" + uuid + "&asyncId=" + asyncId)
                .build();

        try {
//            System.out.println("pinging");
            OkHttpClient client = getOkHttpClient().clone();
            client.setReadTimeout(5, TimeUnit.SECONDS);
            Response response = client.newCall(request).execute();
            try {
                if (response.code() != 200) {
                    throw new IOException("Heartbeat returned HTTP " + response.code());
                }
            } finally {
                response.body().close();
            }
//            System.out.println("pinged");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Heartbeat failed for task " + asyncId, e);
        }
    }
}

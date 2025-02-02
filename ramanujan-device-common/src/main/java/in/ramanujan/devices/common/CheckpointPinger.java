package in.ramanujan.devices.common;

import com.squareup.okhttp.*;
import in.ramanujan.devices.common.Credentials.Credentials;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.Processor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static in.ramanujan.devices.common.OrchestratorApiCallHelper.*;

public class CheckpointPinger {
    private static String heartBeatUri = "/pings/heartbeat";


    static CompletableFuture startTimer(final Processor processor, final Credentials credentials) {
        return CompletableFuture.runAsync(() -> {
            while(true) {
                System.out.println("Checkpointing");
                pingCheckpoint(processor.getCheckpoint(), credentials);
                System.out.println("Checkpointed");
            }
        });
    }

    private static void pingCheckpoint(Checkpoint checkpoint, Credentials credentials) {
        Request request = new Request.Builder()
                .post(RequestBody.create(JSON, ""))
              //  .url(host + heartBeatUri + "?uuid=" + uuid)
                .build();

        try {
            getOkHttpClient().newCall(request).execute().body().close();
        } catch (Exception e) {

        }
    }
}

package in.ramanujan.devices.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.checkpointing.ICheckpointPushClient;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static in.ramanujan.devices.common.OrchestratorApiCallHelper.*;

public class CheckpointPushClient implements ICheckpointPushClient {

    private String uri = "/pings/checkpoint";
    private final ObjectMapper objectMapper = new ObjectMapper();
    final static ExecutorService executorService = Executors.newFixedThreadPool(16);
    final String asyncId;

    public CheckpointPushClient(String asyncId) {
        this.asyncId = asyncId;
    }

    @Override
    public Future<Void> call(Checkpoint checkpoint) {
        try {
            CheckpointWrapper checkpointWrapper = new CheckpointWrapper(checkpoint, asyncId);
            Request request = new Request.Builder()
                    .post(RequestBody.create(JSON, objectMapper.writeValueAsString(checkpointWrapper)))
                    .url(host + uri + "?asyncId=" + asyncId)
                    .build();
            return executorService.submit(() -> {
                try {
                    Response response = getOkHttpClient().newCall(request).execute();
                } catch (IOException e) {

                }
                return null;
            });
        } catch (Exception e) {
            return null;
        }
    }


    @AllArgsConstructor
    private static class CheckpointWrapper {
        public Checkpoint checkpoint;
        public String uuid;
    }
}

package in.ramanujan.devices.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.OkHttpClient;
import in.ramanujan.devices.common.Credentials.Credentials;
import in.ramanujan.devices.common.logging.Logger;
import in.ramanujan.devices.common.logging.LoggerFactory;
import in.ramanujan.devices.common.pojo.OpenPingApiResponse;
import in.ramanujan.devices.common.pojo.OpenPingHttpResponse;
import in.ramanujan.devices.common.pojo.ResultSubmitPayload;
import in.ramanujan.pojo.storage.ResultUploadTarget;

import java.util.Date;
import java.util.UUID;
import java.util.Collections;
import java.util.Map;
import java.util.Base64;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.ramanujan.devices.common.OrchestratorApiCallHelper.*;

public class Orchestrator {

    private Credentials credentials;

    private String pingUri = "/pings/open";
    private String completionUri = "/task/complete";
    private String debugValuePush = "/debugValues";

    private ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger;
    private LoggerFactory loggerFactory;

    final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            String hostId = UUID.randomUUID().toString();
            while (true) {
                try {
                    Thread.sleep(1_000L);
                    final OpenPingApiResponse openPingApiResponse = callBackendOpenAPI(hostId, credentials);
                    if (openPingApiResponse == null || openPingApiResponse.getRuleEngineInput() == null) {
                        Thread.sleep(15_000L);
                        continue;
                    }
                    ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(task -> {
                        Thread thread = new Thread(task, "task-heartbeat");
                        thread.setDaemon(true);
                        return thread;
                    });
                    try {
                        heartbeats.scheduleWithFixedDelay(() -> HeartbeatPinger.pingHeartbeat(
                                hostId, openPingApiResponse.getUuid(), credentials), 0, 5, TimeUnit.SECONDS);
                        logger.info("starting processing " + hostId);
                        DebugClient debugClient = new DebugClient(openPingApiResponse.getUuid());
                        CodeExecutor.ProcessorFutureMap processorFutureMap =
                                CodeExecutor.execute(openPingApiResponse, hostId, debugClient);
                        while (!processorFutureMap.getDone()) {
                            Thread.sleep(10);
                        }
                        if (processorFutureMap.getFailure() != null) {
                            throw new IllegalStateException("Computation failed", processorFutureMap.getFailure());
                        }
                        logger.info("done processing " + hostId);
                        if (Boolean.TRUE.equals(openPingApiResponse.getDebug())) {
                            Long startForDebugSend = new Date().toInstant().toEpochMilli();
                            debugClient.call(processorFutureMap.getDebugData());
                            logger.info("debug api done in " + (new Date().toInstant().toEpochMilli() - startForDebugSend));
                        }
                        submitResults(processorFutureMap, openPingApiResponse.getUuid(), hostId,
                                openPingApiResponse.getResultUpload());
                        logger.info("submitted results of processing " + hostId);
                    } finally {
                        heartbeats.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    logger.error("Worker task failed for " + hostId, e);
                }
            }
        }
    };

    private void submitDebugValues(String asyncId, String data) {
        try {
            Request request = new Request.Builder()
                    .post(RequestBody.create(JSON, data))
                    .url(host + debugValuePush + "?asyncId=" + asyncId)
                    .build();
            Response response = getOkHttpClient().newCall(request).execute();

            if (response.code() != 200) {
                response.body().close();
                submitDebugValues(asyncId, data);
            } else {
                response.body().close();
            }
        } catch (Exception e) {
            System.out.println("send of debug value failed for " + e);
            e.printStackTrace();
            try {
                Thread.sleep(10_000L);
            } catch (Exception ex) {}
            submitDebugValues(asyncId, data);
        }
    }

    private void submitDebugValuesIntellegent(String asyncId, ResultSubmitPayload.UserReadableDebugPoints userReadableDebugPoints) throws Exception {
        String debugValues = objectMapper.writeValueAsString(userReadableDebugPoints);
        int size = 1024 * 1024;//1 MB block

        int counter = 0;
        for (int start = 0; start < debugValues.length(); start += size) {
            submitDebugValues(asyncId + "_" + counter, debugValues.substring(start, Math.min(debugValues.length(), start + size)));
            counter++;
        }
    }

    private void submitResults(final CodeExecutor.ProcessorFutureMap processorFutureMap,
                               final String uuid, final String hostId, ResultUploadTarget target) throws Exception {
        ResultSubmitPayload payload = new ResultSubmitPayload(uuid, hostId, processorFutureMap.getResult());
        byte[] upload = null;
        OkHttpClient uploadClient = getOkHttpClient().clone();
        uploadClient.setWriteTimeout(120, TimeUnit.SECONDS);
        if (target != null) {
            upload = objectMapper.writeValueAsBytes(Collections.singletonMap("map", processorFutureMap.getResult()));
            payload.setData(null);
            payload.setResultObject(target.getObjectName());
            payload.setResultSize((long) upload.length);
            payload.setResultMd5(Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("MD5").digest(upload)));
        }
        Request completion = new Request.Builder()
                .post(RequestBody.create(JSON, objectMapper.writeValueAsString(payload)))
                .url(host + completionUri).build();
        boolean uploaded = target == null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!uploaded) {
                    if (System.currentTimeMillis() >= target.getExpiresAt()) {
                        throw new IllegalStateException("Result upload URL expired");
                    }
                    Request.Builder request = new Request.Builder().url(target.getUploadUrl())
                            .put(RequestBody.create(com.squareup.okhttp.MediaType.parse("application/json"), upload));
                    for (Map.Entry<String, String> header : target.getHeaders().entrySet()) {
                        request.header(header.getKey(), header.getValue());
                    }
                    Response response = uploadClient.newCall(request.build()).execute();
                    try {
                        // A retry can observe the immutable object created by a previous timed-out PUT.
                        if (!response.isSuccessful() && response.code() != 412) {
                            if (isPermanentFailure(response.code())) {
                                throw new IllegalStateException("Result upload rejected with HTTP " + response.code());
                            }
                            throw new IOException("Result upload returned HTTP " + response.code());
                        }
                    } finally {
                        response.body().close();
                    }
                    uploaded = true;
                    logger.info("Uploaded result for task " + uuid + " (" + upload.length + " bytes)");
                }
                Response response = uploadClient.newCall(completion).execute();
                try {
                    if (response.code() != 200) {
                        if (isPermanentFailure(response.code())) {
                            throw new IllegalStateException("Task completion rejected with HTTP " + response.code());
                        }
                        throw new IOException("Task completion returned HTTP " + response.code());
                    }
                } finally {
                    response.body().close();
                }
                return;
            } catch (IOException e) {
                logger.error("Result submission failed for task " + uuid + "; retrying", e);
                Thread.sleep(10_000L);
            }
        }
        throw new InterruptedException("Result submission interrupted");
    }

    private boolean isPermanentFailure(int status) {
        return status >= 400 && status < 500 && status != 408 && status != 429;
    }

    private OpenPingApiResponse callBackendOpenAPI(final String uuid, final Credentials credentials) throws Exception {
        Request request = new Request.Builder()
                .post(RequestBody.create(JSON, ""))
                .url(host + pingUri + "?uuid=" + uuid)
                .build();
        final Response response = getOkHttpClient().newCall(request).execute();
        if(response.code() != 200) {
            response.body().close();
            throw new Exception("Status code not 200");
        }
        try {
            final OpenPingHttpResponse openPingHttpResponse = objectMapper.readValue(response.body().string(), OpenPingHttpResponse.class);
            return openPingHttpResponse.getData();
        } catch (Exception e) {
            throw e;
        } finally {
            response.body().close();
        }
    }


    public Orchestrator(final LoggerFactory loggerFactory) {
        this.logger = loggerFactory.getLogger(Orchestrator.class);
    }

    public void setTimer() {
        new Thread(runnable).start();

//        CompletableFuture.runAsync(runnable).exceptionally(new Function<Throwable, Void>() {
//            @Override
//            public Void apply(Throwable throwable) {
//                System.out.println(throwable);
//                return null;
//            }
//        });
    }
}

package in.ramanujan.devices.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import in.ramanujan.devices.common.Credentials.Credentials;
import in.ramanujan.devices.common.logging.Logger;
import in.ramanujan.devices.common.logging.LoggerFactory;
import in.ramanujan.devices.common.pojo.OpenPingApiResponse;
import in.ramanujan.devices.common.pojo.OpenPingHttpResponse;
import in.ramanujan.devices.common.pojo.ResultSubmitPayload;

import java.util.Date;
import java.util.UUID;

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
                        continue;
                    }
                    logger.info("starting processing " + hostId);
                    DebugClient debugClient = new DebugClient(openPingApiResponse.getUuid());
                    CodeExecutor.ProcessorFutureMap processorFutureMap = CodeExecutor.execute(openPingApiResponse, hostId, debugClient);

                    Long prevTime = new Date().toInstant().toEpochMilli();
                    while(!processorFutureMap.getDone()) {
                        if((new Date().toInstant().toEpochMilli() - prevTime) > 5000) {
                            HeartbeatPinger.pingHeartbeat(hostId, openPingApiResponse.getUuid(), credentials);
                            prevTime = new Date().toInstant().toEpochMilli();
                        }
                    }
                    logger.info("done processing " + hostId);
                    if(openPingApiResponse.getDebug()) {
                        Long startForDebugSend = new Date().toInstant().toEpochMilli();
                        debugClient.call(processorFutureMap.getDebugData());
                        logger.info("debug api done in " + (new Date().toInstant().toEpochMilli() - startForDebugSend));
                    }
                    submitResults(processorFutureMap, credentials, openPingApiResponse.getUuid(), hostId);
                    logger.info("submitted results of  processing " + hostId);
                    //hostId = UUID.randomUUID().toString();
                } catch (Exception e) {
                    //logger.error("topLevel exception", e);
                    continue;
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

    private void submitResults(final CodeExecutor.ProcessorFutureMap processorFutureMap, final Credentials credentials,
                               final String uuid, final String hostId) {
        try {
            ResultSubmitPayload resultSubmitPayload = new ResultSubmitPayload(uuid, hostId, processorFutureMap.getResult());
            Request request = new Request.Builder()
                    .post(RequestBody.create(JSON, objectMapper.writeValueAsString(resultSubmitPayload)))
                    .url(host + completionUri)
                    .build();
            Response response = getOkHttpClient().newCall(request).execute();

            if (response.code() != 200) {
                response.body().close();
                Thread.sleep(10_000L);
                submitResults(processorFutureMap, credentials, uuid, hostId);
            } else {
                response.body().close();
            }
        } catch (Exception e) {
            System.out.println("send of result failed " + e);
            e.printStackTrace();
            try {
                Thread.sleep(10_000L);
            } catch (Exception ex) {}
            submitResults(processorFutureMap, credentials, uuid, hostId);
        }
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

package in.ramanujan.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.data.db.dao.AsyncTaskDao;
import in.ramanujan.data.db.dao.OrchestratorAsyncTaskDao;
import in.ramanujan.data.db.dao.StorageDao;
import in.ramanujan.middleware.base.configuration.ConfigKey;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import in.ramanujan.middleware.base.enums.Status;
import in.ramanujan.middleware.base.pojo.CheckpointResumePayload;
import in.ramanujan.middleware.base.pojo.DeviceExecStatus;
import in.ramanujan.middleware.base.pojo.HostResult;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.HashMap;
import java.util.Map;

public class OrchestrationApiCaller {

    public OrchestratorAsyncTaskDao orchestratorAsyncTaskDao;
    public AsyncTaskDao asyncTaskDao;
    public StorageDao storageDao;

    private Logger logger = LoggerFactory.getLogger(OrchestrationApiCaller.class);

    //@Value("${orchestration.run.code.uri}")
    private String orchestrationRunCodeUri = "/orchestrate";

//    @Value("${orchestration.poll.status.uri}")
    private String orchestrationPollStatusUri = "/status";

//    @Value("{orchestration.poll.interval}")
    private String pollTime = "50";

    private String orchestrationSuspendUri = "/suspend";

    private WebClient webClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    private Gson gson = new Gson();

    public Vertx vertx;
    
    public Context context;


    private WebClient getWebClient() {
        if(webClient == null) {
            webClient = WebClient.create(
                    vertx,
                    new WebClientOptions()
                            .setDefaultHost(ConfigurationGetter.getString(ConfigKey.ORCHESTRATOR_HOST_KEY))
                            .setDefaultPort(ConfigurationGetter.getInt(ConfigKey.ORCHESTRATOR_PORT_KEY))
                            .setMaxPoolSize(100)
            );
        }
        return webClient;
    }
    
    public String urlStr ="";

    public void setWebClient(String ip, int port) {
        webClient = WebClient.create(
                vertx,
                new WebClientOptions()
                        .setDefaultHost(ip)
                        .setDefaultPort(port)
                        .setMaxPoolSize(100)
        );
        urlStr = "http://" + ip + ":" + port;
    }



    public Future<Map<String, Object>> runCode(String asyncId, String firstCommandId,
                                               String dagElementId, Vertx vertx, String orchestratorAsyncId, Boolean toBeDebugged, String commaSeparatedDebugLines) {
        Future<Map<String, Object>> future = Future.future();

        JsonObject payload = new JsonObject().put("orchestratorAsyncId", orchestratorAsyncId)
        .put("firstCommandId", firstCommandId).put("dagElementId", dagElementId).put("debug", toBeDebugged)
                .put("debugLines", commaSeparatedDebugLines);

        retryGetAsyncTaskId(asyncId, payload, vertx)
                .setHandler(new MonitoringHandler<>("orchestratorAsyncTaskCompleteAdd", getAsyncTaskIdHandler -> {
            if(getAsyncTaskIdHandler.succeeded()) {
                String orchestrationId = getAsyncTaskIdHandler.result();
                orchestratorAsyncTaskDao.addMapping(asyncId, orchestrationId, dagElementId).setHandler(handler -> {
                    future.complete();
                    //retryPollOrchestration(asyncId, orchestrationId, vertx, dagElementId, future);
                });
            } else {
                future.fail(getAsyncTaskIdHandler.cause());
            }
        }));


        return future;
    }

    public Future<DeviceExecStatus> callStatusApi(String asyncId, String orchestrationTaskId, String dagElementId) {
        Future<DeviceExecStatus> future = Future.future();
        context.executeBlocking(blocking -> {
            try {
                HttpClient client = new HttpClient("GET", urlStr + orchestrationPollStatusUri, null, new HashMap<String, String>(){{put("uuid", orchestrationTaskId);}});
                if(client.statusCode != 200) {
                    blocking.fail("Not 200");
                } else {
                    JsonObject jsonObject = new JsonObject(client.response);
                    blocking.complete(jsonObject);
                }
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, new MonitoringHandler<>("getOrchestratorStatus", handler -> {
            handlerCallStatus(asyncId, orchestrationTaskId, dagElementId, future, handler);
        }));
//        getWebClient().get(orchestrationPollStatusUri).addQueryParam("uuid", orchestrationTaskId)
//                .send(new MonitoringHandler<>("getOrchestratorStatus", handler -> {
//                    handlerCallStatus(asyncId, orchestrationTaskId, dagElementId, future, handler);
//                }));
        return future;
    }

    private void handlerCallStatus(String asyncId, String orchestrationTaskId, String dagElementId, Future<DeviceExecStatus> future, AsyncResult<Object> handler) {
        if(handler.succeeded()) {
            try {
                JsonObject response = (JsonObject) handler.result();
                String status = response.getString("status");
                if(Status.SUCCESS.getKeyName().equalsIgnoreCase(status)) {
                    logger.info("Task success " + orchestrationTaskId);
                    getData(orchestrationTaskId).setHandler(getDataHandler -> {
                        Map<String, Object>  data = getDataHandler.result();
                        orchestratorAsyncTaskDao.removeOrchestratorAsyncId(asyncId, dagElementId);
                        future.complete(new DeviceExecStatus(Status.SUCCESS, data));
                    });

                } else {
                    if(Status.PROCESSING.getKeyName().equalsIgnoreCase(status)) {
                        logger.info("Retry poll for " + orchestrationTaskId + " as its processing.");
                        future.complete(null);
                    } else if(Status.CHECKPOINT.getKeyName().equalsIgnoreCase(status)) {
                        future.complete(new DeviceExecStatus(Status.CHECKPOINT, new HashMap<>()));
                    } else {
                        future.fail("Got FAILURE");
                    }
                }
            } catch (Exception e) {
                logger.error("Got exception while getting status for " + orchestrationTaskId, e);
                reCallStatusApi(asyncId, orchestrationTaskId, dagElementId, future);
            }
        } else {
            logger.error("Orchestration status API failed", handler.cause());
            reCallStatusApi(asyncId, orchestrationTaskId, dagElementId, future);
        }
    }

    private Future<Map<String, Object>> getData(String orchestrationTaskId) {
        Future<Map<String, Object>> future = Future.future();
        try {
            storageDao.getDagElementResult(orchestrationTaskId).setHandler(handler -> {
                if(handler.succeeded()) {
                    try {
                        future.complete(objectMapper.readValue((String) handler.result(), HostResult.class).getMap());
                    } catch (Exception e) {
                        future.fail(e);
                    }
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    private Future<DeviceExecStatus> reCallStatusApi(String asyncId, String orchestrationTaskId, String dagElementId, Future<DeviceExecStatus> future) {
        return callStatusApi(asyncId, orchestrationTaskId, dagElementId).setHandler(reHandler -> {
            if (reHandler.succeeded()) {
                future.complete(reHandler.result());
            } else {
                future.fail(reHandler.cause());
            }
        });
    }


//    private void pollOrchestrator(String asyncId, String orchestrationTaskId, Vertx vertx, String dagElementId,
//                                  Future<Map<String, Object>> future) {
//
//        getWebClient().get(orchestrationPollStatusUri).addQueryParam("uuid", orchestrationTaskId)
//                .send(handler -> {
//                    if(handler.succeeded()) {
//                        try {
//                            JsonObject response = handler.result().bodyAsJsonObject();
//                            String status = response.getString("status");
//                            Map<String, Object>  data = (Map)((Map) response.getMap().get("data")).get("data");
//                            if(Status.SUCCESS.getKeyName().equalsIgnoreCase(status)) {
//                                logger.info("Task success " + orchestrationTaskId);
//                                orchestratorAsyncTaskDao.removeOrchestratorAsyncId(asyncId, dagElementId);
//                                future.complete(data);
//                            } else {
//                                if(Status.PROCESSING.getKeyName().equalsIgnoreCase(status)) {
//                                    logger.info("Retry poll for " + orchestrationTaskId + " because in processing state");
//                                    retryPollOrchestration(asyncId, orchestrationTaskId, vertx, dagElementId, future);
//                                } else {
//                                    future.fail("Got FAILURE");
//                                }
//                            }
//                        } catch (Exception e) {
//                            logger.error("Retry poll for " + orchestrationTaskId, e);
//                            retryPollOrchestration(asyncId, orchestrationTaskId, vertx, dagElementId, future);
//                        }
//                    } else {
//                        logger.error("Retry poll for " + orchestrationTaskId, handler.cause());
//                        retryPollOrchestration(asyncId, orchestrationTaskId, vertx, dagElementId, future);
//                    }
//                });
//
//    }
//
//    private void retryPollOrchestration(String asyncId, String orchestrationTaskId, Vertx vertx, String dagElementId,
//                                        Future<Map<String, Object>> future) {
//
//        vertx.executeBlocking(blocking -> {
//            try {
//                Thread.sleep(Integer.parseInt(pollTime));
//            } catch (Exception e) {};
//            blocking.complete();
//        }, false, res -> {
//            logger.info("Retry poll calling for " + orchestrationTaskId);
//            orchestratorAsyncTaskDao.isPresent(asyncId, dagElementId).setHandler(handler -> {
//                if(handler.failed() || handler.result()) {
//                    pollOrchestrator(asyncId, orchestrationTaskId, vertx, dagElementId, future);
//                } else {
//                    future.complete();
//                }
//            });
//        });
//    }

    private Future<String> retryGetAsyncTaskId(String asyncId, JsonObject payload, Vertx vertx) {
        logger.info("retryGetAsyncTaskId called for " + payload.getString("firstCommandId"));
        Future<String> future = Future.future();
        getAsyncTaskId(asyncId, payload).setHandler(handler -> {
            if(handler.succeeded()) {
                future.complete(handler.result());
            } else {
                logger.error("Error in getting asyncTaskId from orchestratorService", handler.cause());
                vertx.executeBlocking(blocking -> {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {}
                    blocking.complete();
                }, false, res -> {
                    retryGetAsyncTaskId(asyncId, payload, vertx).setHandler(retry -> {
                        if(retry.succeeded()) {
                            future.complete(retry.result());
                        } else {
                            future.fail(retry.cause());
                        }
                    }) ;
                });
            }
        });

        return  future;
    }

    private Future<String> getAsyncTaskId(String asyncId, JsonObject payload) {
        Future<String> future = Future.future();
        context.executeBlocking(blocking -> {
            try {
                HttpClient client = new HttpClient("POST", urlStr + orchestrationRunCodeUri, payload, null);
                if(client.statusCode != 200) {
                    blocking.fail("Not 200");
                } else {
                    JsonObject jsonObject = new JsonObject(client.response);
                    blocking.complete(jsonObject.getString("data"));
                }
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, new MonitoringHandler<>("orchestrateAPI", handler -> {
            if(handler.succeeded()) {
                future.complete((String) handler.result());
            } else {
                future.fail(handler.cause());
            }
        }));
//        getWebClient().post(orchestrationRunCodeUri).sendJsonObject(payload, new MonitoringHandler<>("orchestrateAPI", res -> {
//            if(res.succeeded()) {
//                try {
//                    if(res.result().statusCode() == 200) {
//                        JsonObject response = res.result().bodyAsJsonObject();
//                        future.complete(response.getString("data"));
//                    } else {
//                        future.fail("api gave status " + res.result().statusCode());
//                    }
//                } catch (Exception e) {
//                    future.fail(e);df55582304bc
//                }
//            } else {
//                future.fail(res.cause());
//            }
//        }));

        return future;
    }

    public Future<Void> disableOrchestrationTaskId(String taskId) {
        Future<Void> future = Future.future();
        webClient.put(orchestrationSuspendUri).addQueryParam("asyncId", taskId).send(handler -> {
            if(handler.succeeded()) {
                future.complete();
            } {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    public Future<Void> resumeCheckpoint(String orchestratorAsyncId, CheckpointResumePayload checkpointResumePayload) {
        Future<Void> future = Future.future();
        return future;
    }
}

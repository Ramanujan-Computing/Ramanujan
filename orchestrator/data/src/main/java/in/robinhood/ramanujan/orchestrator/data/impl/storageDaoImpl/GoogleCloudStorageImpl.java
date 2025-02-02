package in.robinhood.ramanujan.orchestrator.data.impl.storageDaoImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.google.common.collect.Lists;
import in.ramanujan.monitoringutils.StatsRecorderUtils;
import in.robinhood.ramanujan.orchestrator.base.configuration.ConfigConstants;
import in.robinhood.ramanujan.orchestrator.base.configuration.ConfigKey;
import in.robinhood.ramanujan.orchestrator.base.configuration.ConfigurationGetter;
import in.robinhood.ramanujan.orchestrator.base.pojo.CheckpointResumePayload;
import in.robinhood.ramanujan.orchestrator.data.dao.StorageDao;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.checkpoint.Checkpoint;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class GoogleCloudStorageImpl implements StorageDao {

    private final String projectId = "ramanujan-340512";
    private final String dagElementResultBucketName = "dag_element_result";
    private final String dagElementInputBucketName = "dag_element_input";
    private final String dagElementDebugValue = "dag-element-debug-value";
    private final String dagElementCheckpointBucket = "dag-element-checkpoint";
    private final String dagElementDebugPointsBucket = "dag-element-debugpoints";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Logger logger = LoggerFactory.getLogger(GoogleCloudStorageImpl.class);

    private GoogleCredentials storageWriteCredentials;

    private Context context;

    public void init(Context context) {
        this.context = context;
    }

    private GoogleCredentials getStorageWriteCred() throws Exception {
        if(storageWriteCredentials == null) {
            setCreds();
        }
        return storageWriteCredentials;
    }

    private synchronized void setCreds() throws IOException {
        storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream("/OrchestratorCloudStorageWrite.json"))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
    }

    public void init() {
        getContext().executeBlocking(blocking -> {
            try {
                runGcpStoreCode("a1ps123", dagElementInputBucketName, "{}");
                logger.info("GCP code ran !!");
            } catch (Exception e) {
                logger.error(e);
            }
            blocking.complete();
        }, false, handler -> {});
    }


    @Override
    public Future<Void> storeAsyncTaskResult(String asyncTaskId, Object object) throws Exception {
        Future<Void> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                runGcpStoreCode(asyncTaskId, dagElementResultBucketName, object);
                blocking.complete();
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                future.complete();
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    private Context getContext() {
        return context;
    }

    @Override
    public Future<Void> storeDebugResult(String asyncTaskId, Object debugObj) throws Exception {
        Future<Void> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                runGcpStoreCode(asyncTaskId, dagElementDebugValue, objectMapper.writeValueAsString(debugObj));
                blocking.complete();
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                future.complete();
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    @Override
    public Future<RuleEngineInput> getAsyncTaskRuleEngineInput(String asyncTaskId) throws Exception {
        Future<RuleEngineInput> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                String data = getDataFromGCP(asyncTaskId, dagElementInputBucketName);
                blocking.complete(data);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                try {
                    if(((String)handler.result()).isEmpty()) {
                        future.complete(null);
                        return;
                    }
                    future.complete(objectMapper.readValue((String) handler.result(), RuleEngineInput.class));
                } catch (Exception e) {
                    future.fail(e);
                }
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Void> storeCheckpoint(String asyncId, Checkpoint checkpoint) {
        Future<Void> future = Future.future();
        try {
            getContext().executeBlocking(blocking -> {
                try {
                    runGcpStoreCode(asyncId, dagElementCheckpointBucket, objectMapper.writeValueAsString(checkpoint));
                    blocking.complete();
                } catch (Exception e) {
                    blocking.fail(e);
                }
            }, false, handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Checkpoint> getCheckpoint(String asyncId) {
        Future<Checkpoint> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                String data = getDataFromGCP(asyncId, dagElementCheckpointBucket);
                blocking.complete(data);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                try {
                    if(((String)handler.result()).isEmpty()) {
                        future.complete(null);
                        return;
                    }
                    future.complete(objectMapper.readValue((String) handler.result(), Checkpoint.class));
                } catch (Exception e) {
                    future.fail(e);
                }
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    @Override
    public Future<Void> storeBreakpoints(String asyncId, CheckpointResumePayload checkpointResumePayload) {
        Future<Void> future = Future.future();
        try {
            getContext().executeBlocking(blocking -> {
                try {
                    runGcpStoreCode(asyncId, dagElementDebugPointsBucket, objectMapper.writeValueAsString(checkpointResumePayload));
                    blocking.complete();
                } catch (Exception e) {
                    blocking.fail(e);
                }
            }, false, handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<CheckpointResumePayload> getBreakpoints(String asyncId) {
        Future<CheckpointResumePayload> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                String data = getDataFromGCP(asyncId, dagElementDebugPointsBucket);
                blocking.complete(data);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                try {
                    if(((String)handler.result()).isEmpty()) {
                        future.complete(null);
                        return;
                    }
                    future.complete(objectMapper.readValue((String) handler.result(), CheckpointResumePayload.class));
                } catch (Exception e) {
                    future.fail(e);
                }
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    private String getDataFromGCP(String objectId, String bucketName) throws Exception {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(getStorageWriteCred()).build().getService();
        try {
            byte[] content = storage.readAllBytes(bucketName, objectId);
            Long start = new Date().toInstant().toEpochMilli();
            String result = new String(content, StandardCharsets.UTF_8);
            StatsRecorderUtils.record("or_storage_get", new Date().toInstant().toEpochMilli() - start);
            return result;
        } catch (StorageException ex) {
            return "";
        }
    }

    private void runGcpStoreCode(String asyncTaskId, String buckName, Object object) throws Exception {
        Storage storage;
        if(ConfigConstants.DEV.equalsIgnoreCase(ConfigurationGetter.getString(ConfigKey.ENV))) {
            storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        } else {
            storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(getStorageWriteCred()).build().getService();
        }
        BlobId blobId = BlobId.of(buckName, asyncTaskId);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        byte[] content = object.toString().getBytes(StandardCharsets.UTF_8);
        Long start = new Date().toInstant().toEpochMilli();
        storage.createFrom(blobInfo, new ByteArrayInputStream(content));
        StatsRecorderUtils.record("or_storage_store", new Date().toInstant().toEpochMilli() - start);
    }
}

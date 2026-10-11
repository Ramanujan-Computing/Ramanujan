package in.ramanujan.orchestrator.data.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.db.layer.enums.StorageType;
import in.ramanujan.orchestrator.base.pojo.CheckpointResumePayload;
import in.ramanujan.orchestrator.data.impl.storageDaoImpl.GoogleCloudStorageImpl;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.pojo.storage.ResultUploadTarget;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public abstract class StorageDao {
    private final String dagElementResultBucketName = "dag_element_result";
    private final String dagElementInputBucketName = "dag_element_input";
    private final String dagElementDebugValue = "dag-element-debug-value";
    private final String dagElementCheckpointBucket = "dag-element-checkpoint";
    private final String dagElementDebugPointsBucket = "dag-element-debugpoints";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Logger logger = LoggerFactory.getLogger(GoogleCloudStorageImpl.class);

    private Context context;

    public void init(Context context, StorageType storageType) {
        this.context = context;
    }
    protected Context getContext() {
        return context;
    }

    public boolean supportsResultUploads() {
        return false;
    }

    public Future<ResultUploadTarget> createResultUploadTarget(String taskId, String hostId) {
        if (!supportsResultUploads()) {
            return Future.succeededFuture(null);
        }
        Future<ResultUploadTarget> future = Future.future();
        getContext().<ResultUploadTarget>executeBlocking(blocking -> {
            try {
                String prefix = resultUploadPrefix(taskId, hostId);
                blocking.complete(createSignedResultUpload(prefix + UUID.randomUUID(),
                        dagElementResultBucketName));
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, future);
        return future;
    }

    public Future<Void> acceptResultUpload(String taskId, String hostId, String objectName,
                                                long size, String md5) {
        Future<Void> future = Future.future();
        try {
            String prefix = resultUploadPrefix(taskId, hostId);
            if (!supportsResultUploads() || objectName == null || !objectName.startsWith(prefix)
                    || size <= 0 || md5 == null || md5.isEmpty()) {
                return Future.failedFuture(new IllegalArgumentException("Invalid result upload reference"));
            }
            String attempt = objectName.substring(prefix.length());
            if (!UUID.fromString(attempt).toString().equals(attempt)) {
                return Future.failedFuture(new IllegalArgumentException("Invalid result upload attempt"));
            }
        } catch (IllegalArgumentException e) {
            return Future.failedFuture(e);
        }
        getContext().<Void>executeBlocking(blocking -> {
            try {
                promoteResultUpload(objectName, taskId, dagElementResultBucketName, size, md5);
                blocking.complete();
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, future);
        return future;
    }

    private String resultUploadPrefix(String taskId, String hostId) {
        if (taskId == null || hostId == null || !UUID.fromString(taskId).toString().equals(taskId)
                || !UUID.fromString(hostId).toString().equals(hostId)) {
            throw new IllegalArgumentException("Invalid task or host identifier");
        }
        return "uploads/" + taskId + "/" + hostId + "/";
    }

    protected ResultUploadTarget createSignedResultUpload(String objectName, String bucketName) throws Exception {
        throw new UnsupportedOperationException("Signed uploads are not supported by this storage backend");
    }

    protected void promoteResultUpload(String objectName, String taskId, String bucketName,
                                       long size, String md5) throws Exception {
        throw new UnsupportedOperationException("Result uploads are not supported by this storage backend");
    }

    public Future<Void> storeAsyncTaskResult(String asyncTaskId, Object object) throws Exception {
        Future<Void> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                setObject(asyncTaskId, dagElementResultBucketName, object.toString(), 5);
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

    public Future<Void> storeDebugResult(String asyncTaskId, Object debugObj) throws Exception {
        Future<Void> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                setObject(asyncTaskId, dagElementDebugValue, objectMapper.writeValueAsString(debugObj), 5);
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

    public Future<RuleEngineInput> getAsyncTaskRuleEngineInput(String asyncTaskId) throws Exception {
        Future<RuleEngineInput> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                logger.info("asyncTaskId: " + asyncTaskId + "; bucket: " + dagElementInputBucketName);
                String data = getObject(asyncTaskId, dagElementInputBucketName);
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

    public Future<Void> storeCheckpoint(String asyncId, Checkpoint checkpoint) {
        Future<Void> future = Future.future();
        try {
            getContext().executeBlocking(blocking -> {
                try {
                    setObject(asyncId, dagElementCheckpointBucket, objectMapper.writeValueAsString(checkpoint), 5);
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

    public Future<Checkpoint> getCheckpoint(String asyncId) {
        Future<Checkpoint> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                String data = getObject(asyncId, dagElementCheckpointBucket);
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

    public Future<Void> storeBreakpoints(String asyncId, CheckpointResumePayload checkpointResumePayload) {
        Future<Void> future = Future.future();
        try {
            getContext().executeBlocking(blocking -> {
                try {
                    setObject(asyncId, dagElementDebugPointsBucket, objectMapper.writeValueAsString(checkpointResumePayload), 5);
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

    public Future<CheckpointResumePayload> getBreakpoints(String asyncId) {
        Future<CheckpointResumePayload> future = Future.future();
        getContext().executeBlocking(blocking -> {
            try {
                String data = getObject(asyncId, dagElementDebugPointsBucket);
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

    protected abstract String getObject(String objectId, String bucketName) throws Exception ;
    protected abstract void setObject(String objectId, String buckName, String object, int retries) throws Exception;
}

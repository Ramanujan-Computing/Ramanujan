package in.ramanujan.orchestrator.data.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.db.layer.enums.StorageType;
import in.ramanujan.orchestrator.base.pojo.CheckpointResumePayload;
import in.ramanujan.orchestrator.data.impl.storageDaoImpl.GoogleCloudStorageImpl;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.stereotype.Component;

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

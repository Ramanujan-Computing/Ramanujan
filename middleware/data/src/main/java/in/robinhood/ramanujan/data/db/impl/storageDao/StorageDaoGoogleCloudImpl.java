package in.robinhood.ramanujan.data.db.impl.storageDao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.robinhood.ramanujan.data.db.dao.StorageDao;
import in.robinhood.ramanujan.middleware.base.UserReadableDebugPoints;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

@Component
public class StorageDaoGoogleCloudImpl implements StorageDao {

    private final String projectId = "ramanujan-340512";
    private final String dagElementResultBucketName = "dag_element_result";
    private final String dagElementInputBucketName = "dag_element_input";
    private final String dageElementCodeBucketName = "dag-element-code";
    private final String commonFunctionCodeBucket = "common-function-code";
    private final String dagElementDebugValue = "dag-element-debug-value";
    private GoogleCredentials storageWriteCredentials;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Context context;


    private GoogleCredentials getStorageWriteCred() throws Exception {
        if(storageWriteCredentials == null) {
            storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream("/MiddlewareCloudStorageWrite.json"))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        }
        return storageWriteCredentials;
    }

    private Context getVertxContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public Future<Object> getDagElementResult(String objectId) throws Exception {
        Future<Object> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                Object data = getObjectFromGCP(objectId, dagElementResultBucketName);
                blocking.complete(data);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, new MonitoringHandler<>("getDagElementResultFromStorage", handler -> {
            if(handler.succeeded()) {
                future.complete(handler.result());
            } else {
                future.fail(handler.cause());
            }
        }));
        return future;
    }

    private String getObjectFromGCP(String objectId, String bucketName) throws Exception {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(getStorageWriteCred()).build().getService();
        byte[] content = storage.readAllBytes(bucketName, objectId);
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    public Future<String> getCommonCode(String asyncId) {
        Future<String> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                String object = getObjectFromGCP(asyncId, commonFunctionCodeBucket);
                blocking.complete(object);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, new MonitoringHandler<>("getCommonCode", handler -> {
            if(handler.succeeded()) {
                future.complete((String) handler.result());
            } else {
                future.fail(handler.cause());
            }
        }));
        return future;
    }

    @Override
    public Future<UserReadableDebugPoints> getDebugPoints(String orchestratorAsyncId) {
        Future<UserReadableDebugPoints> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                String object = getObjectFromGCP(orchestratorAsyncId, dagElementDebugValue);
                blocking.complete(objectMapper.readValue(object, UserReadableDebugPoints.class));
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, new MonitoringHandler<>("getDebugPoints", handler -> {
            if(handler.succeeded()) {
                future.complete((UserReadableDebugPoints) handler.result());
            } else {
                future.fail(handler.cause());
            }
        }));
        return future;
    }

    @Override
    public Future<String> getDagElementCode(String dagElementId) {
        Future<String> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                String object = getObjectFromGCP(dagElementId, dageElementCodeBucketName);
                blocking.complete(object);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, new MonitoringHandler<>("getDagElementCode", handler -> {
            if(handler.succeeded()) {
                future.complete((String) handler.result());
            } else {
                future.fail(handler.cause());
            }
        }));
        return future;
    }

    @Override
    public Future<Void> storeCommonCode(String asyncId, String code) {
        Future<Void> future = Future.future();
        try {
            getVertxContext().executeBlocking(blocking -> {
                try {
                    runGcpStoreCode(asyncId, commonFunctionCodeBucket, code);
                    blocking.complete();
                } catch (Exception e) {
                    blocking.fail(e);
                }
            }, false, new MonitoringHandler<>("storeCommonCode", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> storeDagElementCode(String dagElementId, String code) {
        Future<Void> future = Future.future();
        try {
            getVertxContext().executeBlocking(blocking -> {
                try {
                    runGcpStoreCode(dagElementId, dageElementCodeBucketName, code);
                    blocking.complete();
                } catch (Exception e) {
                    blocking.fail(e);
                }
            }, false, new MonitoringHandler<>("storeDagElementCode", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> storeDagElement(String orchestratorAsyncId, RuleEngineInput ruleEngineInput) throws Exception {
        Future<Void> future = Future.future();
        try {
            getVertxContext().executeBlocking(blocking -> {
                try {
                    runGcpStoreCode(orchestratorAsyncId, dagElementInputBucketName, objectMapper.writeValueAsString(ruleEngineInput));
                    blocking.complete();
                } catch (Exception e) {
                    blocking.fail(e);
                }
            }, false, new MonitoringHandler<>("storeDagElementInStorage", handler -> {
                if(handler.succeeded()) {
                    future.complete();
                } else {
                    future.fail(handler.cause());
                }
            }));
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    private void runGcpStoreCode(String asyncTaskId, String buckName, Object object) throws Exception {
        final Storage storage = StorageOptions.newBuilder().setProjectId(projectId)
                .setCredentials(getStorageWriteCred()).build().getService();

        BlobId blobId = BlobId.of(buckName, asyncTaskId);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        byte[] content = object.toString().getBytes(StandardCharsets.UTF_8);
        storage.createFrom(blobInfo, new ByteArrayInputStream(content));
    }
}

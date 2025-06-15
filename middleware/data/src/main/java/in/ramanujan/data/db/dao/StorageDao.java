package in.ramanujan.data.db.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.db.layer.enums.StorageType;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.translation.codeConverter.BasicDagElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.UserReadableDebugPoints;
import io.vertx.core.Context;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

@Component
public abstract class  StorageDao {

    private final ObjectMapper objectMapper = new ObjectMapper();
    protected final String dagElementResultBucketName = "dag_element_result";
    protected final String dagElementBucketName = "dag-element";
    protected final String dagElementInputBucketName = "dag_element_input";
    protected final String dageElementCodeBucketName = "dag-element-code";
    protected final String commonFunctionCodeBucket = "common-function-code";
    protected final String dagElementDebugValue = "dag-element-debug-value";

    private Context context;

    private Context getVertxContext() {
        return context;
    }

    public void setContext(Context context, StorageType storageType) {
        this.context = context;
    }

    public final Future<Object> getDagElementResult(String objectId) throws Exception {
        Future<Object> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                Object data = getObject(objectId, dagElementResultBucketName);
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

    public final Future<String> getCommonCode(String asyncId) {
        Future<String> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                String object = getObject(asyncId, commonFunctionCodeBucket);
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

    public final Future<UserReadableDebugPoints> getDebugPoints(String orchestratorAsyncId) {
        Future<UserReadableDebugPoints> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                String object = getObject(orchestratorAsyncId, dagElementDebugValue);
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

    public final Future<String> getDagElementCode(String dagElementId) {
        Future<String> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                String object = getObject(dagElementId, dageElementCodeBucketName);
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

    public final  Future<Void> storeCommonCode(String asyncId, String code) {
        Future<Void> future = Future.future();
        try {
            getVertxContext().executeBlocking(blocking -> {
                try {
                    setObject(asyncId, commonFunctionCodeBucket, code);
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

    public final Future<Void> storeDagElementCode(String dagElementId, String code) {
        Future<Void> future = Future.future();
        try {
            getVertxContext().executeBlocking(blocking -> {
                try {
                    setObject(dagElementId, dageElementCodeBucketName, code);
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

    public final Future<Void> storeDagElement(DagElement dagElement) {
        Future<Void> future = Future.future();
        try {
            getVertxContext().executeBlocking(blocking -> {
                try {
                    BasicDagElement basicDagElement = new BasicDagElement();
                    basicDagElement.setId(dagElement.getId());
                    basicDagElement.setFirstCommandId(dagElement.getFirstCommandId());
                    basicDagElement.setRuleEngineInput(dagElement.getRuleEngineInput());

                    setObject(dagElement.getId(), dagElementBucketName, objectMapper.writeValueAsString(basicDagElement));
                    blocking.complete();
                } catch (Exception e) {
                    blocking.fail(e);
                }
            }, false, new MonitoringHandler<>("storeDagElement", handler -> {
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

    public final Future<BasicDagElement> getDagElement(String dagElementId) {
        Future<BasicDagElement> future = Future.future();
        getVertxContext().executeBlocking(blocking -> {
            try {
                String object = getObject(dagElementId, dagElementBucketName);
                BasicDagElement basicDagElement = objectMapper.readValue(object, BasicDagElement.class);
                blocking.complete(basicDagElement);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, new MonitoringHandler<>("getDagElement", handler -> {
            if(handler.succeeded()) {
                future.complete((BasicDagElement) handler.result());
            } else {
                future.fail(handler.cause());
            }
        }));
        return future;
    }

    public final Future<Void> storeDagElementInput(String orchestratorAsyncId, RuleEngineInput ruleEngineInput) {
        Future<Void> future = Future.future();
        try {
            getVertxContext().executeBlocking(blocking -> {
                try {
                    setObject(orchestratorAsyncId, dagElementInputBucketName, objectMapper.writeValueAsString(ruleEngineInput));
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

    protected abstract void setObject(String objectId, String buckName, String object) throws Exception;
    protected abstract String getObject(String objectId, String bucketName) throws Exception;
}


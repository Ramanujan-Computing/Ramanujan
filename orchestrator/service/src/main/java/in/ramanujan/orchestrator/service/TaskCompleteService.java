package in.ramanujan.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.base.pojo.HostResult;
import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TaskCompleteService {

    @Autowired
    private AsyncTaskHostMappingDao asyncTaskHostMappingDao;

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private StorageDao storageDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Future<Void> completeTask(String hostId, String uuid, Object resultOfComputation) {
        return completeTask(hostId, uuid, () -> {
            HostResult hostResult = new HostResult();
            hostResult.setMap((Map<String, Object>) resultOfComputation);
            return refreshVariables(uuid, hostResult);
        });
    }

    public Future<Void> completeUploadedTask(String hostId, String uuid, String objectName,
                                             long size, String md5) {
        return completeTask(hostId, uuid,
                () -> storageDao.acceptResultUpload(uuid, hostId, objectName, size, md5));
    }

    private Future<Void> completeTask(String hostId, String uuid,
                                      java.util.function.Supplier<Future<?>> persistResult) {
        Future<Void> future = Future.future();
        asyncTaskDao.getAsyncTask(uuid).setHandler(taskHandler -> {
            if (taskHandler.failed()) {
                future.fail(taskHandler.cause());
                return;
            }
            AsyncTask task = taskHandler.result();
            if (task == null || task.getUuid() == null) {
                future.fail(new IllegalArgumentException("Unknown task"));
                return;
            }
            if (Status.SUCCESS.getKeyName().equals(task.getStatus())) {
                asyncTaskHostMappingDao.removeMapping(hostId, uuid).setHandler(future);
                return;
            }
            if (!Status.PROCESSING.getKeyName().equals(task.getStatus())) {
                future.fail(new IllegalStateException("Task is not accepting results"));
                return;
            }
            asyncTaskHostMappingDao.getHostForTask(uuid).setHandler(hostHandler -> {
                if (hostHandler.failed()) {
                    future.fail(hostHandler.cause());
                    return;
                }
                if (hostId == null || !hostId.equals(hostHandler.result())) {
                    future.fail(new IllegalStateException("Result submitted by a worker that no longer owns the task"));
                    return;
                }
                try {
                    persistResult.get().setHandler(stored -> {
                        if (stored.failed()) {
                            future.fail(stored.cause());
                        } else {
                            updateAsyncTask(uuid, hostId, future);
                        }
                    });
                } catch (RuntimeException e) {
                    future.fail(e);
                }
            });
        });
        return future;
    }

    private Future<Object> refreshVariables(String uuid, HostResult hostResult) {
        Future<Object> future = Future.future();
        try {
            storageDao.storeAsyncTaskResult(uuid, JsonObject.mapFrom(hostResult).toString()).setHandler(handler -> {
                if (handler.succeeded()) {
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

    private void updateAsyncTask(String uuid, String hostId, Future<Void> future) {
        JsonObject updateQuery = new JsonObject()
                .put(AsyncTaskFields.status.getFieldName(), Status.SUCCESS.getKeyName());
        asyncTaskDao.update(uuid, updateQuery.getMap()).setHandler(updateAsyncTaskHandler -> {
            if(updateAsyncTaskHandler.succeeded())  {
                asyncTaskHostMappingDao.removeMapping(hostId, uuid).setHandler(taskRemoveHandler -> {
                    if(taskRemoveHandler.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(taskRemoveHandler.cause());
                    }
                });
            } else {
                future.fail(updateAsyncTaskHandler.cause());
            }
        });
    }
}

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
        Future<Void> future = Future.future();

        HostResult hostResult = new HostResult();
        hostResult.setMap((Map<String, Object>) resultOfComputation);

        refreshVariables(uuid, hostResult).setHandler(refreshVariableHandler -> {
            if(refreshVariableHandler.succeeded()) {
                updateAsyncTask(uuid, hostId, future);
            } else {
                future.fail(refreshVariableHandler.cause());
            }
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

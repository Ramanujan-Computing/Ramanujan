package in.robinhood.ramanujan.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.robinhood.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.robinhood.ramanujan.orchestrator.base.enums.Status;
import in.robinhood.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.robinhood.ramanujan.orchestrator.base.pojo.HostResult;
import in.robinhood.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.robinhood.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import in.robinhood.ramanujan.orchestrator.data.dao.StorageDao;
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

    public Future<Void> completeTask(String hostId, Object resultOfComputation) {
        Future<Void> future = Future.future();
        asyncTaskHostMappingDao.getMapping(hostId).setHandler(mappedAsyncTaskHandler -> {
           if(mappedAsyncTaskHandler.succeeded()) {
               AsyncTask asyncTask = mappedAsyncTaskHandler.result();
               HostResult hostResult = new HostResult();
               hostResult.setMap((Map<String, Object>) resultOfComputation);

               refreshVariables(asyncTask, hostResult).setHandler(refreshVariableHandler -> {
                  if(refreshVariableHandler.succeeded()) {
                      updateAsyncTask(asyncTask, hostId, future);
                  } else {
                      future.fail(refreshVariableHandler.cause());
                  }
               });
           } else {
               future.fail(mappedAsyncTaskHandler.cause());
           }
        });

        return future;
    }

    private Future<Object> refreshVariables(AsyncTask asyncTask, HostResult hostResult) {
        Future<Object> future = Future.future();
        try {
            storageDao.storeAsyncTaskResult(asyncTask.getUuid(), JsonObject.mapFrom(hostResult).toString()).setHandler(handler -> {
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

    private void updateAsyncTask(AsyncTask asyncTask, String hostId, Future<Void> future) {
        JsonObject updateQuery = new JsonObject()
                .put(AsyncTaskFields.status.getFieldName(), Status.SUCCESS.getKeyName());
        asyncTaskDao.update(asyncTask.getUuid(), updateQuery.getMap()).setHandler(updateAsyncTaskHandler -> {
            if(updateAsyncTaskHandler.succeeded())  {
                asyncTaskHostMappingDao.removeMapping(hostId, asyncTask.getUuid()).setHandler(taskRemoveHandler -> {
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

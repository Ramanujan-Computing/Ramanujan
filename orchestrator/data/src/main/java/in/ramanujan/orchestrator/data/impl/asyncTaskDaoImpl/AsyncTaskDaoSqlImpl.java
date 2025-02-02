package in.ramanujan.orchestrator.data.impl.asyncTaskDaoImpl;

import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.AsyncTaskOrchestrator;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AsyncTaskDaoSqlImpl implements AsyncTaskDao {

    @Autowired
    private QueryExecutor queryExecutor;

    @Override
    public Future<Void> insert(AsyncTask asyncTask) {
        Future<Void> future = Future.future();
        try {
            AsyncTaskOrchestrator asyncTaskOrchestrator = new AsyncTaskOrchestrator();
            asyncTaskOrchestrator.setFirstCommandId(asyncTask.getFirstCommandId());
            asyncTaskOrchestrator.setStatus(asyncTask.getStatus());
            asyncTaskOrchestrator.setUuid(asyncTask.getUuid());
            asyncTaskOrchestrator.setDebug(asyncTask.getDebug().toString());
            queryExecutor.execute(asyncTaskOrchestrator, null, QueryType.INSERT).setHandler(handler -> {
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
    public Future<Void> update(String uuid, Map<String, Object> updateQuery) {
        Future<Void> future = Future.future();
        try {
            AsyncTaskOrchestrator asyncTaskOrchestrator = new AsyncTaskOrchestrator();
            asyncTaskOrchestrator.setUuid(uuid);
            asyncTaskOrchestrator.setStatus((String) updateQuery.get(AsyncTaskFields.status.getFieldName()));
            queryExecutor.execute(asyncTaskOrchestrator, Keys.UUID, QueryType.UPDATE).setHandler(handler -> {
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
    public Future<AsyncTask> getAsyncTask(String uuid) {
        Future<AsyncTask> future = Future.future();
        try {
            AsyncTaskOrchestrator asyncTaskOrchestrator = new AsyncTaskOrchestrator();
            asyncTaskOrchestrator.setUuid(uuid);
            queryExecutor.execute(asyncTaskOrchestrator, Keys.UUID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   AsyncTask asyncTask = new AsyncTask();
                   List<Object> objectList = handler.result();
                   if(objectList != null && objectList.size() > 0) {
                       AsyncTaskOrchestrator resultObj = (AsyncTaskOrchestrator) objectList.get(0);
                       asyncTask.setStatus(resultObj.getStatus());
                       asyncTask.setFirstCommandId(resultObj.getFirstCommandId());
                       asyncTask.setDebug(Boolean.valueOf(resultObj.getDebug()));
                       asyncTask.setUuid(uuid);
                   }
                   future.complete(asyncTask);
               } else {
                   future.fail(handler.cause());
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

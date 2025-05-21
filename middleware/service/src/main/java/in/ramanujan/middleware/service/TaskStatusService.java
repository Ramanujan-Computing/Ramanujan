package in.ramanujan.middleware.service;

import in.ramanujan.data.db.dao.AsyncTaskDao;
import in.ramanujan.data.db.dao.VariableValueDao;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import in.ramanujan.translation.codeConverter.pojo.VariableAndArrayResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusService {

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private VariableValueDao variableValueDao;

    public Future<AsyncTask> getAsyncTaskStatus(String asyncTaskId) {
        Promise<AsyncTask> promise = Promise.promise();
        asyncTaskDao.getAsyncTask(asyncTaskId).setHandler(handler -> {
            if(handler.succeeded()) {
                AsyncTask asyncTask = handler.result();
                if(asyncTask.getTaskStatus() == AsyncTask.TaskStatus.SUCCESS) {
                    variableValueDao.getAllValuesForAsyncId(asyncTaskId).setHandler(getValueHandler -> {
                        VariableAndArrayResult result = getValueHandler.result();
                        asyncTask.setResult(result);
                        promise.complete(asyncTask);
                    });
                } else {
                    promise.complete(handler.result());
                }
            } else {
                promise.fail(handler.cause());
            }
        });
        return promise.future();
    }
}

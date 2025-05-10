package in.ramanujan.middleware.service;

import in.ramanujan.data.db.dao.AsyncTaskDao;
import in.ramanujan.data.db.dao.VariableValueDao;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import io.vertx.core.Future;

public class TaskStatusService {
    public AsyncTaskDao asyncTaskDao;
    public VariableValueDao variableValueDao;

    public Future<AsyncTask> getAsyncTaskStatus(String asyncTaskId) {
        Future<AsyncTask> future = Future.future();
        asyncTaskDao.getAsyncTask(asyncTaskId).setHandler(handler -> {
            if(handler.succeeded()) {
                AsyncTask asyncTask = handler.result();
                if(asyncTask.getTaskStatus() == AsyncTask.TaskStatus.SUCCESS) {
                    variableValueDao.getAllValuesForAsyncId(asyncTaskId).setHandler(getValueHandler -> {
                        asyncTask.setResult(getValueHandler.result());
                        future.complete(asyncTask);
                    });
                } else {
                    future.complete(handler.result());
                }
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }
}

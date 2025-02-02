package in.robinhood.ramanujan.middleware.service;


import in.robinhood.ramanujan.data.db.dao.AsyncTaskDao;
import in.robinhood.ramanujan.data.db.dao.VariableValueDao;
import in.robinhood.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusService {

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private VariableValueDao variableValueDao;

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

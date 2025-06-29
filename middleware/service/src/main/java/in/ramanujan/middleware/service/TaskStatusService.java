package in.ramanujan.middleware.service;

import in.ramanujan.data.db.dao.AsyncTaskDao;
import in.ramanujan.data.db.dao.DagElementDao;
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

    @Autowired
    private DagElementDao dagElementDao;

    public Future<AsyncTask> getAsyncTaskStatus(String asyncTaskId) {
        Future<AsyncTask> future = Future.future();
        dagElementDao.isAsyncTaskDone(asyncTaskId).setHandler(asyncDoneHandler -> {
           if(asyncDoneHandler.succeeded()) {
               if(asyncDoneHandler.result() == 0 ) {
                   AsyncTask asyncTask = new AsyncTask();
                     asyncTask.setTaskId(asyncTaskId);
                        asyncTask.setTaskStatus(AsyncTask.TaskStatus.SUCCESS);
                   variableValueDao.getAllValuesForAsyncId(asyncTaskId).setHandler(getValueHandler -> {
                          if(getValueHandler.failed()) {
                            future.fail(getValueHandler.cause());
                            return;
                          }
                       VariableAndArrayResult result = getValueHandler.result();
                       asyncTask.setResult(result);
                       future.complete(asyncTask);
                     });
               } else {
                   asyncTaskDao.getAsyncTask(asyncTaskId).setHandler(handler -> {
                          if(handler.succeeded()) {
                            AsyncTask asyncTask = handler.result();
                                 future.complete(asyncTask);
                          } else {
                            future.fail(handler.cause());
                          }
                   });
               }
           } else {
               future.fail(asyncDoneHandler.cause());
               return;
           }
        });
        return  future;
    }
}

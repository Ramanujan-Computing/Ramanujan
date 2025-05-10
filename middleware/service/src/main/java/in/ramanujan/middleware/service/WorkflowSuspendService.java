package in.ramanujan.middleware.service;

import in.ramanujan.data.OrchestrationApiCaller;
import in.ramanujan.data.db.dao.AsyncTaskDao;
import in.ramanujan.data.db.dao.OrchestratorAsyncTaskDao;
import in.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WorkflowSuspendService {
    public OrchestratorAsyncTaskDao orchestratorAsyncTaskDao;
    public OrchestrationApiCaller orchestrationApiCaller;
    public AsyncTaskDao asyncTaskDao;

    public Future<Void> suspendWf(String asyncId) throws Exception {
        Future<Void> future = Future.future();
        orchestratorAsyncTaskDao.getMapping(asyncId).setHandler(handler -> {
            if (handler.succeeded()) {
                List<Future> disableTaskFutureList = new ArrayList<>();
                if(handler.result() != null) {
                    for (String dagElementId : handler.result().keySet()) {
                        String orchestratorId = handler.result().get(dagElementId);
                        disableTaskFutureList.add(disableOrchTaskId(asyncId, orchestratorId, dagElementId));
                    }
                }
                CompositeFuture.join(disableTaskFutureList).setHandler(compositeFutureAsyncResultHandler -> {
                    orchestratorAsyncTaskDao.remove(asyncId).setHandler(removeHandler -> {
                        asyncTaskDao.update(asyncId, new HashMap<String, Object>() {
                            {
                                put("taskStatus", AsyncTask.TaskStatus.FAILED);
                            }
                        }).setHandler(asyncTaskFailHandler -> {
                            future.complete();
                        });
                    });
                });
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }

    private Future<Void> disableOrchTaskId(final String asyncTaskId, final String orchId, final String dagElementId) {
        Future<Void> future = Future.future();
        orchestrationApiCaller.disableOrchestrationTaskId(orchId).setHandler(handler -> {
            orchestratorAsyncTaskDao.removeOrchestratorAsyncId(asyncTaskId, dagElementId).setHandler(dbHandler -> {
                future.complete();
            });
        });
        return future;
    }
}

package in.robinhood.ramanujan.middleware.service;

import in.robinhood.ramanujan.data.KafkaManagerApiCaller;
import in.robinhood.ramanujan.data.OrchestrationApiCaller;
import in.robinhood.ramanujan.data.db.dao.AsyncTaskDao;
import in.robinhood.ramanujan.data.db.dao.OrchestratorAsyncTaskDao;
import in.robinhood.ramanujan.middleware.base.pojo.CheckpointResumePayload;
import in.robinhood.ramanujan.middleware.base.pojo.asyncTask.AsyncTask;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class CheckpointResumeService {

    @Autowired
    private OrchestrationApiCaller orchestrationApiCaller;

    @Autowired
    private OrchestratorAsyncTaskDao orchestratorAsyncTaskDao;

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private KafkaManagerApiCaller kafkaManagerApiCaller;

    public Future<Void> resumeCheckpoint(String asyncId, String dagElementId, CheckpointResumePayload checkpointResumePayload) {
        Future future = Future.future();
        orchestratorAsyncTaskDao.getMapping(asyncId).setHandler(handler -> {
            if(handler.succeeded()) {
                final String orchestratorId = handler.result().get(dagElementId);
                orchestrationApiCaller.resumeCheckpoint(orchestratorId, checkpointResumePayload).setHandler(resumeCheckpointCaller -> {
                    if(resumeCheckpointCaller.succeeded()) {
                        asyncTaskDao.update(asyncId, new HashMap<String, Object>() {
                            {
                                put("taskStatus", AsyncTask.TaskStatus.PENDING);
                                put("result", null);
                            }
                        }).setHandler(asyncTaskUpdateHandler -> {
                           if(asyncTaskUpdateHandler.succeeded()) {
                               kafkaManagerApiCaller.callEventApi(asyncId, dagElementId, true).setHandler(kafkaManagerApiCallerHandler -> {
                                  if(kafkaManagerApiCallerHandler.succeeded()) {
                                      future.complete();
                                  } else {
                                      future.fail(kafkaManagerApiCallerHandler.cause());
                                  }
                               });
                           } else {
                               future.fail(asyncTaskUpdateHandler.cause());
                           }
                        });
                    } else {
                        future.fail(resumeCheckpointCaller.cause());
                    }
                });
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }
}

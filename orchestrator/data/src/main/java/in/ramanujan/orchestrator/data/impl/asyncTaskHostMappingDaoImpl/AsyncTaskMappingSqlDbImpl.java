package in.ramanujan.orchestrator.data.impl.asyncTaskHostMappingDaoImpl;

import com.google.common.base.Strings;
import in.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.HostMapping;
import in.ramanujan.db.layer.utils.QueryExecutor;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.ramanujan.orchestrator.data.impl.asyncTaskDaoImpl.AsyncTaskDaoSqlImpl;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class AsyncTaskMappingSqlDbImpl implements AsyncTaskHostMappingDao {

    @Autowired
    private QueryExecutor queryExecutor;

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    Logger logger= LoggerFactory.getLogger(AsyncTaskMappingSqlDbImpl.class);

    @Override
    public Future<Void> createMapping(AsyncTask asyncTask, String hostMachineId, Boolean resumeComputation) {
        Future<Void> future = Future.future();
        try {
            HostMapping hostMapping = new HostMapping();
            hostMapping.setHostId(hostMachineId);
            hostMapping.setUuid(asyncTask.getUuid());
            hostMapping.setLastPing(new Date().toInstant().toEpochMilli());
            hostMapping.setResumeComputation(resumeComputation.toString());
            queryExecutor.execute(hostMapping, null, QueryType.INSERT).setHandler(handler -> {
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
    public Future<Void> deleteTask(String asyncTaskId) {
        try {
            HostMapping hostMapping = new HostMapping();
            hostMapping.setUuid(asyncTaskId);
            Future<Void> future = Future.future();
            queryExecutor.execute(hostMapping, Keys.UUID, QueryType.DELETE).setHandler(handler -> {
               if(handler.succeeded()) {
                   future.complete();
               } else {
                   future.fail(handler.cause());
               }
            });
            return future;
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<String> getHostForTask(String asyncTaskId) {
        Future<String> future = Future.future();
        try {
            HostMapping hostMapping = new HostMapping();
            hostMapping.setUuid(asyncTaskId);
            queryExecutor.execute(hostMapping, Keys.UUID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   if(handler.result().size() > 0) {
                       future.complete(((HostMapping)handler.result().get(0)).getHostId());
                   } else {
                       future.complete();
                   }
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
    public Future<AsyncTask> getMapping(String hostMachineId) {
        Future<AsyncTask> future = Future.future();
        try {
            HostMapping hostMapping = new HostMapping();
            hostMapping.setHostId(hostMachineId);
            queryExecutor.execute(hostMapping, Keys.HOST_ID, QueryType.SELECT).setHandler(handler -> {
                if(handler.succeeded()) {
                    if(handler.result() == null || handler.result().size() == 0) {
                        future.complete();
                        return;
                    }
                    if(handler.result().size() > 1) {
                        logger.info("mapping more than 1 : " + handler.result().size());
                    }
                    String uuid = ((HostMapping) handler.result().get(0)).getUuid();
                    asyncTaskDao.getAsyncTask(uuid).setHandler(getAsyncTaskHandler -> {
                        if(getAsyncTaskHandler.succeeded()) {
                            if(getAsyncTaskHandler.result() instanceof AsyncTaskDaoSqlImpl.DummyAsyncTask) {
                                logger.error("Host assigned is null for async task: " + uuid);
                                try {
                                    queryExecutor.execute(hostMapping, Keys.HOST_ID, QueryType.DELETE).setHandler(deleteHandler -> {
                                        if (deleteHandler.succeeded())
                                            future.complete();
                                        else
                                            future.fail(deleteHandler.cause());
                                    });
                                    return;
                                } catch (Exception ex) {
                                    future.fail(ex);
                                    return;
                                }

                            }
                            AsyncTask asyncTask = getAsyncTaskHandler.result();
                            if("true".equals(((HostMapping)handler.result().get(0)).getResumeComputation())) {
                                asyncTask.setCheckpoint(new Checkpoint());
                            }
                            future.complete(asyncTask);
                        } else {
                            future.fail(getAsyncTaskHandler.cause());
                        }
                    });
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
    public Future<Void> update(String hostMachineId, AsyncTask asyncTask) {
        //TODO: To be deprecated.
        return null;
    }

    @Override
    public Future<Void> removeMapping(String hostMachineId, String asyncTaskId) {
        Future<Void> future = Future.future();
        try {
            HostMapping hostMapping = new HostMapping();
            hostMapping.setHostId(hostMachineId);
            hostMapping.setUuid(asyncTaskId);
            queryExecutor.execute(hostMapping, Keys.UUID_HOST_ID, QueryType.DELETE).setHandler(handler -> {
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
}

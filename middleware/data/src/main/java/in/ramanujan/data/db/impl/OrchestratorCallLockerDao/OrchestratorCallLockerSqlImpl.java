package in.ramanujan.data.db.impl.OrchestratorCallLockerDao;

import in.ramanujan.data.db.dao.OrchestratorCallLockerDao;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.OrchestratorCallLocker;
import in.ramanujan.db.layer.utils.QueryExecutor;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrchestratorCallLockerSqlImpl implements OrchestratorCallLockerDao {

    @Autowired
    private QueryExecutor queryExecutor;

    private Logger logger = LoggerFactory.getLogger(OrchestratorCallLockerSqlImpl.class);

    @Override
    public Future<Void> insertLocker(String threadId, String dagElementId, Long timestamp) {
        Future<Void> future = Future.future();
        OrchestratorCallLocker orchestratorCallLocker = new OrchestratorCallLocker();
        orchestratorCallLocker.setMiddlewareThreadId(threadId);
        orchestratorCallLocker.setDagElementId(dagElementId);
        orchestratorCallLocker.setLastUpdate(timestamp);
        try {
            queryExecutor.execute(orchestratorCallLocker, Keys.DAG_ELEMENT_ID, QueryType.INSERT).setHandler(handler -> {
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

    @Override
    public Future<Boolean> attainedLock(String threadId, String dagElementId) {
        OrchestratorCallLocker orchestratorCallLocker = new OrchestratorCallLocker();
        orchestratorCallLocker.setDagElementId(dagElementId);
        try {
            Future<Boolean> future = Future.future();
            queryExecutor.execute(orchestratorCallLocker, Keys.DAG_ELEMENT_ID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   try {
                       List<Object> result = handler.result();
                       List<OrchestratorCallLocker> lockerList = new ArrayList<>();
                       OrchestratorCallLocker threadLocker = null;
                       logger.info("SIZE: " + result.size());
                       for (Object obj : result) {
                           OrchestratorCallLocker locker = (OrchestratorCallLocker) obj;
                           lockerList.add(locker);
                           logger.info(locker.getMiddlewareThreadId() + "; " + locker.getDagElementId() + "; " + locker.getLastUpdate());
                           if (threadId.equalsIgnoreCase(locker.getMiddlewareThreadId())) {
                               threadLocker = locker;
                           }
                       }
                       for (OrchestratorCallLocker locker : lockerList) {
                           if (!threadId.equalsIgnoreCase(locker.getMiddlewareThreadId())) {
                               if (locker.getLastUpdate() < threadLocker.getLastUpdate()) {
                                   future.complete(false);
                                   return;
                               }
                           }
                       }
                       future.complete(true);
                   } catch (Exception e) {
                       logger.error(e);
                   }
               } else {
                   logger.error(handler.cause());
                   future.fail(handler.cause());
               }
            });
            return future;
        } catch (Exception e) {
            logger.error(e);
            return Future.failedFuture(e);
        }
    }
}

package in.ramanujan.middleware.service;

import in.ramanujan.data.db.dao.DagElementDao;
import in.ramanujan.data.db.dao.StorageDao;
import in.ramanujan.middleware.base.pojo.DebugInformation;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

public class DebugInformationFetchService {
    public StorageDao storageDao;
    public DagElementDao dagElementDao;

    public Future<DebugInformation> getDebugInformation(final String asyncId, final String dagElementId) {
        DebugInformation debugInformation = new DebugInformation();
        Future<DebugInformation> debugInformationFuture = Future.future();
        CompositeFuture.all(getCommonCode(asyncId, debugInformation), getDagElementCode(asyncId, dagElementId, debugInformation),
                        getNextDagElementIds(dagElementId, debugInformation), getDagElementDebugValues(dagElementId, debugInformation))
                .setHandler(handler -> {
                   if(handler.succeeded()) {
                       debugInformationFuture.complete(debugInformation);
                   } else {
                       debugInformationFuture.fail(handler.cause());
                   }
                });

        return debugInformationFuture;
    }

    private Future<Void> getDagElementDebugValues(String dagElementId, DebugInformation debugInformation) {
        Future<Void> future = Future.future();
        dagElementDao.getDagElementAndOrchestratorAsyncIdMapping(dagElementId).setHandler(getOrchestratorAsyncIdHandler -> {
            if(getOrchestratorAsyncIdHandler.succeeded()) {
                String orchestratorAsyncId = getOrchestratorAsyncIdHandler.result();
                if(orchestratorAsyncId == null) {
                    future.fail("no orchestratorAsyncId mapping for " + dagElementId);
                    return;
                }
                storageDao.getDebugPoints(orchestratorAsyncId).setHandler(debugPointHandler -> {
                   if(debugPointHandler.succeeded()) {
                       debugInformation.setUserReadableDebugPoint(debugPointHandler.result().getDebugData());
                       future.complete();
                   } else {
                       future.fail(debugPointHandler.cause());
                   }
                });
            } else {
                future.fail(getOrchestratorAsyncIdHandler.cause());
            }
        });
        return future;
    }

    private Future<Void> getCommonCode(String asyncId, DebugInformation debugInformation) {
        Future<Void> future = Future.future();
        storageDao.getCommonCode(asyncId).setHandler(handler -> {
           if(handler.succeeded()) {
               debugInformation.setCommonCode(handler.result());
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }

    private Future<Void> getDagElementCode(String asyncId, String dagElementId, DebugInformation debugInformation) {
        Future<Void> future = Future.future();
        storageDao.getDagElementCode(dagElementId).setHandler(handler -> {
           if(handler.succeeded()) {
               debugInformation.setCode(handler.result());
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }

    private Future<Void> getNextDagElementIds(String dagElementId, DebugInformation debugInformation) {
        Future<Void> future = Future.future();
        //FIX USAGE OF GET_NEXT_ID METHOD!!!
        dagElementDao.getNextId(dagElementId, false).setHandler(handler -> {
           if(handler.succeeded()) {
               debugInformation.setNextDagElementIds(handler.result());
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}

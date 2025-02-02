package in.ramanujan.middleware.service;

import in.ramanujan.data.db.dao.StorageDao;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetDagElementCodeService {

    @Autowired
    private StorageDao storageDao;

    public Future<String> getCode(String asyncId, String dagElementId) {
        Future<String> future = Future.future();
        Future<String> commonCode = storageDao.getCommonCode(asyncId);
        Future<String> dagCode = storageDao.getDagElementCode(dagElementId);
        CompositeFuture.all(commonCode, dagCode).setHandler(handler -> {
           if(handler.succeeded()) {
               StringBuilder builder = new StringBuilder();
               builder.append(commonCode.result()).append("\n").append(dagCode.result());
               future.complete(builder.toString());
           } else {
               future.fail(handler.cause());
           }
        });
        return future;
    }
}

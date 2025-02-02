package in.robinhood.ramanujan.middleware.service.future;

import in.robinhood.ramanujan.data.db.dao.StorageDao;
import io.vertx.core.Future;

public class StoreDagElementCode extends FutureCaller {

    private final String dagElementCode, dagElementId;
    private final StorageDao storageDao;


    public StoreDagElementCode(FutureCaller nextCaller, String dagElementId, String dagElementCode, StorageDao storageDao) {
        super(nextCaller);
        this.storageDao = storageDao;
        this.dagElementCode = dagElementCode;
        this.dagElementId = dagElementId;
    }

    @Override
    Future<Void> callInternal() {
        return storageDao.storeDagElementCode(dagElementId, dagElementCode);
    }
}

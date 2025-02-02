package in.ramanujan.middleware.service.future;

import in.ramanujan.data.db.dao.StorageDao;
import io.vertx.core.Future;

public class CommonCodeStoreFutureCaller extends FutureCaller{

    private final String asyncId, commonCode;
    private final StorageDao storageDao;

    public CommonCodeStoreFutureCaller(FutureCaller nextCaller, String asyncId, String commonCode, StorageDao storageDao) {
        super(nextCaller);
        this.asyncId = asyncId;
        this.commonCode = commonCode;
        this.storageDao = storageDao;
    }

    @Override
    Future<Void> callInternal() {
        return storageDao.storeCommonCode(asyncId, commonCode);
    }
}

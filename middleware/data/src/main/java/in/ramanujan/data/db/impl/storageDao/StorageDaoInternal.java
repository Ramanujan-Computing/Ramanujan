package in.ramanujan.data.db.impl.storageDao;

import in.ramanujan.data.db.dao.StorageDao;
import in.ramanujan.db.layer.enums.StorageType;
import io.vertx.core.Context;
import org.springframework.stereotype.Component;

@Component
public class StorageDaoInternal extends StorageDao {
    private StorageDaoInternal storageDaoInternal;

    @Override
    public void setContext(Context context, StorageType storageType) {
        super.setContext(context, storageType);
        if(storageType == StorageType.GCP) {
            storageDaoInternal = new StorageDaoGoogleCloudImpl();
        } else {
            storageDaoInternal = new StorageDaoLocalContainerImpl();
        }
    }

    @Override
    protected void setObject(String objectId, String buckName, String object, int currentRetryCount) throws Exception {
        storageDaoInternal.setObject(objectId, buckName, object, currentRetryCount);
    }

    @Override
    protected String getObject(String objectId, String bucketName, int currentRetryCount) throws Exception {
        return storageDaoInternal.getObject(objectId, bucketName, currentRetryCount);
    }
}

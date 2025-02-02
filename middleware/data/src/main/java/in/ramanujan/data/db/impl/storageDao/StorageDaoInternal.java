package in.ramanujan.data.db.impl.storageDao;

import in.ramanujan.data.db.dao.StorageDao;
import in.ramanujan.middleware.base.configuration.ConfigurationGetter;
import io.vertx.core.Context;
import org.springframework.stereotype.Component;

@Component
public class StorageDaoInternal extends StorageDao {
    private StorageDaoInternal storageDaoInternal;

    @Override
    public void setContext(Context context, ConfigurationGetter.StorageType storageType) {
        super.setContext(context, storageType);
        if(storageType == ConfigurationGetter.StorageType.GCP) {
            storageDaoInternal = new StorageDaoGoogleCloudImpl();
        } else {
            storageDaoInternal = new StorageDaoLocalContainerImpl();
        }
    }

    @Override
    protected void setObject(String objectId, String buckName, String object) throws Exception {
        storageDaoInternal.setObject(objectId, buckName, object);
    }

    @Override
    protected String getObject(String objectId, String bucketName) throws Exception {
        return storageDaoInternal.getObject(objectId, bucketName);
    }
}

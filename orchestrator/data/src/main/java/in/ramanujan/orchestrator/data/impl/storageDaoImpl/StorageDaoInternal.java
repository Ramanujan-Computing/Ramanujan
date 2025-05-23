package in.ramanujan.orchestrator.data.impl.storageDaoImpl;

import in.ramanujan.orchestrator.base.configuration.ConfigurationGetter;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.Context;
import org.springframework.stereotype.Component;

@Component
public class StorageDaoInternal extends StorageDao {

    private StorageDaoInternal storageDaoInternal;

    @Override
    public void init(Context context, ConfigurationGetter.StorageType storageType) {
        super.init(context, storageType);
        if (storageType == ConfigurationGetter.StorageType.LOCAL) {
            storageDaoInternal = new LocalStorageImpl();
        } else if (storageType == ConfigurationGetter.StorageType.GCP) {
            storageDaoInternal = new GoogleCloudStorageImpl();
        }
    }

    @Override
    protected String getObject(String objectId, String bucketName) throws Exception {
        return storageDaoInternal.getObject(objectId, bucketName);
    }

    @Override
    protected void setObject(String objectId, String buckName, String object) throws Exception {
        storageDaoInternal.setObject(objectId, buckName, object);
    }
}

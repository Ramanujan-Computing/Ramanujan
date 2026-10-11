package in.ramanujan.orchestrator.data.impl.storageDaoImpl;

import in.ramanujan.db.layer.enums.StorageType;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.Context;
import in.ramanujan.pojo.storage.ResultUploadTarget;
import org.springframework.stereotype.Component;

@Component
public class OrchestratorStorageDaoInternal extends StorageDao {

    private OrchestratorStorageDaoInternal storageDaoInternal;

    @Override
    public void init(Context context, StorageType storageType) {
        super.init(context, storageType);
        if (storageType == StorageType.LOCAL) {
            storageDaoInternal = new LocalStorageImpl();
        } else if (storageType == StorageType.GCP) {
            storageDaoInternal = new GoogleCloudStorageImpl();
        }
    }

    @Override
    protected String getObject(String objectId, String bucketName) throws Exception {
        return storageDaoInternal.getObject(objectId, bucketName);
    }

    @Override
    protected void setObject(String objectId, String buckName, String object, int retries) throws Exception {
        storageDaoInternal.setObject(objectId, buckName, object, retries);
    }

    @Override
    public boolean supportsResultUploads() {
        return storageDaoInternal.supportsResultUploads();
    }

    @Override
    protected ResultUploadTarget createSignedResultUpload(String objectName, String bucketName) throws Exception {
        return storageDaoInternal.createSignedResultUpload(objectName, bucketName);
    }

    @Override
    protected void promoteResultUpload(String objectName, String taskId, String bucketName,
                                       long size, String md5) throws Exception {
        storageDaoInternal.promoteResultUpload(objectName, taskId, bucketName, size, md5);
    }
}

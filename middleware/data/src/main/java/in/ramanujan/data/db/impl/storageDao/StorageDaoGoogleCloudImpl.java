package in.ramanujan.data.db.impl.storageDao;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import io.vertx.core.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

//@Component

public class StorageDaoGoogleCloudImpl extends StorageDaoInternal {

    private final String projectId = "ramanujan-340512";
    private GoogleCredentials storageWriteCredentials;
    private Context context;
    private final String credentialsPath;

    private final Logger logger = LoggerFactory.getLogger(StorageDaoGoogleCloudImpl.class);

    public StorageDaoGoogleCloudImpl() {
        // You can change ConfigKey.MIDDLEWARE_GCS_CREDENTIALS_PATH to your actual config key
        this.credentialsPath = in.ramanujan.middleware.base.configuration.ConfigurationGetter.getString(
            in.ramanujan.middleware.base.configuration.ConfigKey.MIDDLEWARE_GCS_CREDENTIALS_PATH
        );
    }

    private StorageDaoGoogleCloudImpl(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    private GoogleCredentials getStorageWriteCred() throws Exception {
        if(storageWriteCredentials == null) {
            storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        }
        return storageWriteCredentials;
    }

    @Override
    protected String getObject(String objectId, String bucketName, int currentRetryCount) throws Exception {
        try {
            Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(getStorageWriteCred()).build().getService();
            byte[] content = storage.readAllBytes(bucketName, objectId);
            return new String(content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            if(currentRetryCount == 0) {
                throw ex;
            }
            logger.error("Error reading object from Google Cloud Storage: {}", ex);
            Thread.sleep(5000);
            return getObject(objectId, bucketName, currentRetryCount - 1);
        }
    }

    @Override
    protected void setObject(String objectId, String buckName, String object, int currentRetryCount) throws Exception {
        try {
            final Storage storage = StorageOptions.newBuilder().setProjectId(projectId)
                    .setCredentials(getStorageWriteCred()).build().getService();

            BlobId blobId = BlobId.of(buckName, objectId);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            byte[] content = object.toString().getBytes(StandardCharsets.UTF_8);
            storage.createFrom(blobInfo, new ByteArrayInputStream(content));
        } catch (IOException ex) {
            if(currentRetryCount == 0) {
                throw ex;
            }
            logger.error("Error writing object to Google Cloud Storage: {}", ex);
            Thread.sleep(5000);
            setObject(objectId, buckName, object, currentRetryCount - 1);
        }
    }


}

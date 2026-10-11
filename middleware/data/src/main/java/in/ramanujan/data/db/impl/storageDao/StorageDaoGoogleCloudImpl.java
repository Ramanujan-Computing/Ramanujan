package in.ramanujan.data.db.impl.storageDao;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ServiceOptions;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class StorageDaoGoogleCloudImpl extends StorageDaoInternal {

    private GoogleCredentials storageWriteCredentials;
    private final String credentialsPath;

    private final Logger logger = LoggerFactory.getLogger(StorageDaoGoogleCloudImpl.class);

    public StorageDaoGoogleCloudImpl() {
        this.credentialsPath = in.ramanujan.middleware.base.configuration.ConfigurationGetter.getString(
            in.ramanujan.middleware.base.configuration.ConfigKey.MIDDLEWARE_GCS_CREDENTIALS_PATH
        );
    }

    public StorageDaoGoogleCloudImpl(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    private String getProjectId() {
        String configured = in.ramanujan.middleware.base.configuration.ConfigurationGetter.getString(
            in.ramanujan.middleware.base.configuration.ConfigKey.GCP_PROJECT_ID
        );
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }
        String envProject = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (envProject != null && !envProject.trim().isEmpty()) {
            return envProject;
        }
        return ServiceOptions.getDefaultProjectId();
    }

    private GoogleCredentials getStorageWriteCred() {
        if (storageWriteCredentials != null) {
            return storageWriteCredentials;
        }
        try {
            if (credentialsPath != null && !credentialsPath.trim().isEmpty() && new File(credentialsPath).exists()) {
                storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                        .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
                return storageWriteCredentials;
            }
            String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (envPath != null && !envPath.trim().isEmpty() && new File(envPath).exists()) {
                storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream(envPath))
                        .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
                return storageWriteCredentials;
            }
            try {
                storageWriteCredentials = GoogleCredentials.getApplicationDefault()
                        .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
                return storageWriteCredentials;
            } catch (Exception ignored) {
            }
        } catch (Exception ex) {
            logger.warn("Could not load GoogleCredentials: " + ex.getMessage());
        }
        return null;
    }

    private Storage getStorage() {
        String projId = getProjectId();
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (projId != null && !projId.trim().isEmpty()) {
            builder.setProjectId(projId);
        }
        GoogleCredentials creds = getStorageWriteCred();
        if (creds != null) {
            builder.setCredentials(creds);
        }
        return builder.build().getService();
    }

    @Override
    protected String getObject(String objectId, String bucketName, int currentRetryCount) throws Exception {
        try {
            Storage storage = getStorage();
            byte[] content = storage.readAllBytes(bucketName, objectId);
            return new String(content, StandardCharsets.UTF_8);
        } catch (StorageException ex) {
            if (ex.getCode() == 404) {
                return "";
            }
            if (currentRetryCount <= 0) {
                throw ex;
            }
            logger.error("Error reading object from Google Cloud Storage: {}", ex);
            Thread.sleep(2000);
            return getObject(objectId, bucketName, currentRetryCount - 1);
        } catch (Exception ex) {
            if (currentRetryCount <= 0) {
                throw ex;
            }
            logger.error("Error reading object from Google Cloud Storage: {}", ex);
            Thread.sleep(2000);
            return getObject(objectId, bucketName, currentRetryCount - 1);
        }
    }

    @Override
    protected void setObject(String objectId, String buckName, String object, int currentRetryCount) throws Exception {
        try {
            final Storage storage = getStorage();
            BlobId blobId = BlobId.of(buckName, objectId);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            byte[] content = object.getBytes(StandardCharsets.UTF_8);
            storage.createFrom(blobInfo, new ByteArrayInputStream(content));
        } catch (Exception ex) {
            if (currentRetryCount <= 0) {
                throw ex;
            }
            logger.error("Error writing object to Google Cloud Storage: {}", ex);
            Thread.sleep(2000);
            setObject(objectId, buckName, object, currentRetryCount - 1);
        }
    }

    @Override
    protected void deleteObject(String objectId, String bucketName, int currentRetryCount) throws Exception {
        try {
            Storage storage = getStorage();
            storage.delete(BlobId.of(bucketName, objectId));
        } catch (Exception ex) {
            if (currentRetryCount <= 0) {
                logger.warn("Could not delete object " + objectId + " in " + bucketName + ": " + ex.getMessage());
                return;
            }
            Thread.sleep(1000);
            deleteObject(objectId, bucketName, currentRetryCount - 1);
        }
    }

    @Override
    protected void deletePrefix(String prefix, String bucketName, int currentRetryCount) throws Exception {
        try {
            Storage storage = getStorage();
            Iterable<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix)).iterateAll();
            for (Blob blob : blobs) {
                blob.delete();
            }
        } catch (Exception ex) {
            if (currentRetryCount <= 0) {
                logger.warn("Could not delete prefix " + prefix + " in " + bucketName + ": " + ex.getMessage());
                return;
            }
            Thread.sleep(1000);
            deletePrefix(prefix, bucketName, currentRetryCount - 1);
        }
    }
}

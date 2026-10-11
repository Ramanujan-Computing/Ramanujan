package in.ramanujan.orchestrator.data.impl.storageDaoImpl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.google.common.collect.Lists;
import in.ramanujan.monitoringutils.StatsRecorderUtils;
import in.ramanujan.orchestrator.base.configuration.ConfigConstants;
import in.ramanujan.orchestrator.base.configuration.ConfigKey;
import in.ramanujan.orchestrator.base.configuration.ConfigurationGetter;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import in.ramanujan.pojo.storage.ResultUploadTarget;


public class GoogleCloudStorageImpl extends OrchestratorStorageDaoInternal {
    private GoogleCredentials storageWriteCredentials;
    private final String projectId = "ramanujan-340512";
    private final String credentialsPath;

    public GoogleCloudStorageImpl() {
        // You can change ConfigKey.ORCHESTRATOR_GCS_CREDENTIALS_PATH to your actual config key
        this.credentialsPath = ConfigurationGetter.getString(ConfigKey.ORCHESTRATOR_GCS_CREDENTIALS_PATH);
    }

    private GoogleCloudStorageImpl(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    protected GoogleCredentials getStorageWriteCred() throws Exception {
        if(storageWriteCredentials == null) {
            setCreds();
        }
        return storageWriteCredentials;
    }

    protected Storage getStorage() throws Exception {
        return StorageOptions.newBuilder().setProjectId(projectId)
                .setCredentials(getStorageWriteCred()).build().getService();
    }

    @Override
    public boolean supportsResultUploads() {
        return true;
    }

    @Override
    protected ResultUploadTarget createSignedResultUpload(String objectName, String bucketName) throws Exception {
        Map<String, String> headers = Collections.singletonMap("x-goog-if-generation-match", "0");
        BlobInfo object = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType("application/json").build();
        ResultUploadTarget target = new ResultUploadTarget();
        target.setUploadUrl(getStorage().signUrl(object, 24, TimeUnit.HOURS,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withContentType(),
                Storage.SignUrlOption.withExtHeaders(headers)).toString());
        target.setObjectName(objectName);
        target.setExpiresAt(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24));
        target.setHeaders(headers);
        return target;
    }

    @Override
    protected void promoteResultUpload(String objectName, String taskId, String bucketName,
                                       long size, String md5) throws Exception {
        Storage storage = getStorage();
        Blob source = storage.get(BlobId.of(bucketName, objectName));
        if (source == null || source.getSize() != size || !md5.equals(source.getMd5())
                || !"application/json".equals(source.getContentType())) {
            throw new IllegalArgumentException("Uploaded result is missing or its size, checksum or content type does not match");
        }
        BlobInfo destination = BlobInfo.newBuilder(bucketName, taskId)
                .setContentType("application/json")
                .setMetadata(Collections.singletonMap("result-upload-object", objectName)).build();
        try {
            storage.copy(Storage.CopyRequest.newBuilder().setSource(source.getBlobId())
                    .setSourceOptions(Storage.BlobSourceOption.generationMatch(source.getGeneration()))
                    .setTarget(destination, Storage.BlobTargetOption.doesNotExist())
                    .build()).getResult();
        } catch (StorageException e) {
            if (e.getCode() != 412) {
                throw e;
            }
            Blob existing = storage.get(BlobId.of(bucketName, taskId));
            if (existing == null || existing.getMetadata() == null
                    || !objectName.equals(existing.getMetadata().get("result-upload-object"))
                    || existing.getSize() != size || !md5.equals(existing.getMd5())) {
                throw new IllegalStateException("A different result has already been accepted for this task", e);
            }
        }
    }

    private synchronized void setCreds() throws IOException {
        if(storageWriteCredentials != null) {
            return;
        }
        if (credentialsPath != null && new java.io.File(credentialsPath).isFile()) {
            storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        } else {
            storageWriteCredentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        }
    }


    @Override
    protected String getObject(String objectId, String bucketName) throws Exception {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(getStorageWriteCred()).build().getService();
        try {
            byte[] content = storage.readAllBytes(bucketName, objectId);
            Long start = new Date().toInstant().toEpochMilli();
            String result = new String(content, StandardCharsets.UTF_8);
            StatsRecorderUtils.record("or_storage_get", new Date().toInstant().toEpochMilli() - start);
            return result;
        } catch (StorageException ex) {
            return "";
        }
    }

    @Override
    protected void setObject(String objectId, String buckName, String object, int retries) throws Exception {
        try {
            Storage storage;
            if (ConfigConstants.DEV.equalsIgnoreCase(ConfigurationGetter.getString(ConfigKey.ENV))) {
                storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
            } else {
                storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(getStorageWriteCred()).build().getService();
            }
            BlobId blobId = BlobId.of(buckName, objectId);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            byte[] content = object.getBytes(StandardCharsets.UTF_8);
            Long start = new Date().toInstant().toEpochMilli();
            storage.createFrom(blobInfo, new ByteArrayInputStream(content));
            StatsRecorderUtils.record("or_storage_store", new Date().toInstant().toEpochMilli() - start);
        } catch (IOException ex) {
            if (retries == 0) {
                throw ex;
            }
            // Retry logic
            Thread.sleep(5000);
            setObject(objectId, buckName, object, retries - 1);
        } catch (Exception e) {
            // Handle other exceptions
            throw new RuntimeException("Error writing object to Google Cloud Storage: " + e.getMessage(), e);
        }
    }
}

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

    private GoogleCredentials getStorageWriteCred() throws Exception {
        if(storageWriteCredentials == null) {
            setCreds();
        }
        return storageWriteCredentials;
    }

    private synchronized void setCreds() throws IOException {
        if(storageWriteCredentials != null) {
            return;
        }
        storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
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
    protected void setObject(String objectId, String buckName, String object) throws Exception {
        Storage storage;
        if(ConfigConstants.DEV.equalsIgnoreCase(ConfigurationGetter.getString(ConfigKey.ENV))) {
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
    }
}

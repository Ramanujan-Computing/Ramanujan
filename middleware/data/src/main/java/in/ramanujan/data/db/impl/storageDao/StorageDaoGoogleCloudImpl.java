package in.ramanujan.data.db.impl.storageDao;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import io.vertx.core.Context;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

//@Component
public class StorageDaoGoogleCloudImpl extends StorageDaoInternal {

    private final String projectId = "ramanujan-340512";
    private GoogleCredentials storageWriteCredentials;
    private Context context;


    private GoogleCredentials getStorageWriteCred() throws Exception {
        if(storageWriteCredentials == null) {
            storageWriteCredentials = GoogleCredentials.fromStream(new FileInputStream("/MiddlewareCloudStorageWrite.json"))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        }
        return storageWriteCredentials;
    }

    @Override
    protected String getObject(String objectId, String bucketName) throws Exception {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(getStorageWriteCred()).build().getService();
        byte[] content = storage.readAllBytes(bucketName, objectId);
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    protected void setObject(String objectId, String buckName, String object) throws Exception {
        final Storage storage = StorageOptions.newBuilder().setProjectId(projectId)
                .setCredentials(getStorageWriteCred()).build().getService();

        BlobId blobId = BlobId.of(buckName, objectId);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        byte[] content = object.toString().getBytes(StandardCharsets.UTF_8);
        storage.createFrom(blobInfo, new ByteArrayInputStream(content));
    }


}

package in.ramanujan.orchestrator.data.impl.storageDaoImpl;

import in.ramanujan.orchestrator.data.dao.StorageDao;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

public class LocalStorageImpl extends StorageDaoInternal {

    /**
     * Get from file /bucketName/objectId
     */
    @Override
    protected String getObject(String objectId, String bucketName) throws Exception {
        File file = new File("/" + bucketName + "/" + objectId);
        try {
            //Read the file
            return new String(Files.readAllBytes(file.toPath()));
        } catch (NoSuchFileException ex) {
            return "";
        }
    }

    @Override
    protected void setObject(String objectId, String buckName, String object) throws Exception {
        File file = new File("/" + buckName + "/" + objectId);
        //Write to the file
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), object.getBytes(StandardCharsets.UTF_8));
    }

}

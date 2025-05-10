package in.ramanujan.data.db.impl.storageDao;

import in.ramanujan.middleware.translation.UserReadableDebugPoints;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

public class StorageDaoLocalContainerImpl extends StorageDaoInternal {

    /**
    * save a file in /buckName/objectId
    */
    @Override
    protected void setObject(String objectId, String buckName, String object) throws Exception {
        try {
            File file = new File("/" + buckName + "/" + objectId);
            // write object to file
            Files.createDirectories(file.toPath().getParent());
            Files.write(file.toPath(), object.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw ex;
        }
    }

    @Override
    protected String getObject(String objectId, String bucketName) throws Exception {
        try {
            File file = new File("/" + bucketName + "/" + objectId);
            return new String(Files.readAllBytes(file.toPath()));
        } catch (NoSuchFileException ex) {
            return "";
        }
    }
}

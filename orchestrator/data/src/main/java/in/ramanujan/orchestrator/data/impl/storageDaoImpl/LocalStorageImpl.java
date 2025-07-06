package in.ramanujan.orchestrator.data.impl.storageDaoImpl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

public class LocalStorageImpl extends OrchestratorStorageDaoInternal {

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
    protected void setObject(String objectId, String buckName, String object, int retries) throws Exception {
        File file = new File("/" + buckName + "/" + objectId);
        //Write to the file
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), object.getBytes(StandardCharsets.UTF_8));
    }

}

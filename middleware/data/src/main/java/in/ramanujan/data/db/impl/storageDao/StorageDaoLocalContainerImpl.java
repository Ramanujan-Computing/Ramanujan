package in.ramanujan.data.db.impl.storageDao;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class StorageDaoLocalContainerImpl extends StorageDaoInternal {

    private static Boolean rootWritable = null;

    private static synchronized boolean isRootWritable() {
        if (rootWritable == null) {
            try {
                File testDir = new File("/tmp_test_write");
                if (testDir.mkdirs()) {
                    testDir.delete();
                    rootWritable = true;
                } else {
                    rootWritable = false;
                }
            } catch (Exception e) {
                rootWritable = false;
            }
        }
        return rootWritable;
    }

    private File getFile(String bucketName, String objectId) {
        if (isRootWritable()) {
            return new File("/" + bucketName + "/" + objectId);
        } else {
            return new File("/tmp/ramanujan-storage/" + bucketName + "/" + objectId);
        }
    }

    /**
    * save a file in /buckName/objectId
    */
    @Override
    protected void setObject(String objectId, String buckName, String object, int currentRetryCount) throws Exception {
        try {
            File file = getFile(buckName, objectId);
            // write object to file
            Files.createDirectories(file.toPath().getParent());
            Files.write(file.toPath(), object.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw ex;
        }
    }

    @Override
    protected String getObject(String objectId, String bucketName, int currentRetryCount) throws Exception {
        try {
            File file = getFile(bucketName, objectId);
            return new String(Files.readAllBytes(file.toPath()));
        } catch (NoSuchFileException ex) {
            return "";
        }
    }

    @Override
    protected void deleteObject(String objectId, String bucketName, int currentRetryCount) throws Exception {
        try {
            File file = getFile(bucketName, objectId);
            if (file.exists()) {
                Files.deleteIfExists(file.toPath());
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void deletePrefix(String prefix, String bucketName, int currentRetryCount) throws Exception {
        try {
            File dir = getFile(bucketName, prefix);
            if (dir.exists()) {
                Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }
}

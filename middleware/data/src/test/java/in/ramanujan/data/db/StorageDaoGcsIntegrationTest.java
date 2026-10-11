package in.ramanujan.data.db;

import in.ramanujan.data.db.impl.storageDao.StorageDaoGoogleCloudImpl;
import in.ramanujan.db.layer.enums.StorageType;
import io.vertx.core.Vertx;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StorageDaoGcsIntegrationTest {

    private StorageDaoGoogleCloudImpl storageDao;
    private Vertx vertx;

    @Before
    public void setup() {
        vertx = Vertx.vertx();
        String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath != null && new File(credPath).exists()) {
            storageDao = new StorageDaoGoogleCloudImpl(credPath);
        } else {
            storageDao = new StorageDaoGoogleCloudImpl();
        }
        storageDao.setContext(vertx.getOrCreateContext(), StorageType.GCP);
    }

    @Test
    public void testStoreGetAndCleanGcs() throws Exception {
        String testAsyncId = "test_run_" + UUID.randomUUID().toString();
        String vertexId1 = "vertex_1";
        String vertexId2 = "vertex_2";
        String childVertexId = "child_1";

        String vertexOutput1 = "{\"dagElementId\":\"vertex_1\",\"arrayMap\":{\"arr\":{\"0\":1.0,\"1\":2.0}}}";
        String vertexOutput2 = "{\"dagElementId\":\"vertex_2\",\"arrayMap\":{\"arr\":{\"2\":3.0,\"3\":4.0}}}";
        String compactedOutput = "{\"dagElementId\":\"child_1\",\"coalescedArrays\":{\"arr\":{\"0\":1.0,\"1\":2.0,\"2\":3.0,\"3\":4.0}}}";

        // 1. Store vertex 1 output
        CountDownLatch latch1 = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        storageDao.storeDagVertexOutput(testAsyncId, vertexId1, vertexOutput1).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latch1.countDown();
        });
        Assert.assertTrue(latch1.await(15, TimeUnit.SECONDS));
        Assert.assertNull("Failed to store vertex 1", errorRef.get());

        // 2. Overwrite vertex 1 output (tests delete-before-create contract)
        String updatedVertex1 = "{\"dagElementId\":\"vertex_1\",\"arrayMap\":{\"arr\":{\"0\":10.0,\"1\":20.0}}}";
        CountDownLatch latchOverwrite = new CountDownLatch(1);
        storageDao.storeDagVertexOutput(testAsyncId, vertexId1, updatedVertex1).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latchOverwrite.countDown();
        });
        Assert.assertTrue(latchOverwrite.await(15, TimeUnit.SECONDS));
        Assert.assertNull("Failed to overwrite vertex 1", errorRef.get());

        // 3. Read back vertex 1 output
        CountDownLatch latchRead1 = new CountDownLatch(1);
        AtomicReference<String> readContent1 = new AtomicReference<>();
        storageDao.getDagVertexOutput(testAsyncId, vertexId1).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            else readContent1.set(res.result());
            latchRead1.countDown();
        });
        Assert.assertTrue(latchRead1.await(15, TimeUnit.SECONDS));
        Assert.assertNull(errorRef.get());
        Assert.assertEquals(updatedVertex1, readContent1.get());

        // 4. Store vertex 2 and compacted output
        CountDownLatch latch2 = new CountDownLatch(1);
        storageDao.storeDagVertexOutput(testAsyncId, vertexId2, vertexOutput2).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latch2.countDown();
        });
        Assert.assertTrue(latch2.await(15, TimeUnit.SECONDS));

        CountDownLatch latchCompacted = new CountDownLatch(1);
        storageDao.storeCompactedOutput(testAsyncId, childVertexId, compactedOutput).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latchCompacted.countDown();
        });
        Assert.assertTrue(latchCompacted.await(15, TimeUnit.SECONDS));

        // Read compacted output
        CountDownLatch latchReadCompacted = new CountDownLatch(1);
        AtomicReference<String> readCompacted = new AtomicReference<>();
        storageDao.getCompactedOutput(testAsyncId, childVertexId).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            else readCompacted.set(res.result());
            latchReadCompacted.countDown();
        });
        Assert.assertTrue(latchReadCompacted.await(15, TimeUnit.SECONDS));
        Assert.assertEquals(compactedOutput, readCompacted.get());

        // 5. Cleanup asyncId (should delete everything under testAsyncId/)
        CountDownLatch latchCleanup = new CountDownLatch(1);
        storageDao.cleanupAsyncId(testAsyncId).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latchCleanup.countDown();
        });
        Assert.assertTrue(latchCleanup.await(15, TimeUnit.SECONDS));
        Assert.assertNull(errorRef.get());

        // 6. Verify deleted
        CountDownLatch latchVerify = new CountDownLatch(1);
        AtomicReference<String> verifyDeleted = new AtomicReference<>();
        storageDao.getDagVertexOutput(testAsyncId, vertexId1).setHandler(res -> {
            if (res.succeeded()) verifyDeleted.set(res.result());
            latchVerify.countDown();
        });
        Assert.assertTrue(latchVerify.await(15, TimeUnit.SECONDS));
        Assert.assertTrue("Object should be deleted/empty after cleanup",
                verifyDeleted.get() == null || verifyDeleted.get().trim().isEmpty());
    }
}

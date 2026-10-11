package in.ramanujan.data.db;

import in.ramanujan.data.db.impl.storageDao.StorageDaoLocalContainerImpl;
import in.ramanujan.db.layer.enums.StorageType;
import io.vertx.core.Vertx;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StorageDaoLocalContainerTest {

    private StorageDaoLocalContainerImpl storageDao;
    private Vertx vertx;

    @Before
    public void setup() {
        vertx = Vertx.vertx();
        storageDao = new StorageDaoLocalContainerImpl();
        storageDao.setContext(vertx.getOrCreateContext(), StorageType.LOCAL);
    }

    @Test
    public void testLocalStoreGetAndCleanup() throws Exception {
        String testAsyncId = "local_async_" + UUID.randomUUID().toString();
        String vertexId1 = "v1";
        String vertexId2 = "v2";
        String childId = "c1";

        String output1 = "{\"dagElementId\":\"v1\",\"variableMap\":{\"a\":1}}";
        String output2 = "{\"dagElementId\":\"v2\",\"variableMap\":{\"b\":2}}";
        String compacted = "{\"dagElementId\":\"c1\",\"coalescedVariables\":{\"a\":1,\"b\":2}}";

        // Store vertex 1
        CountDownLatch latch1 = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        storageDao.storeDagVertexOutput(testAsyncId, vertexId1, output1).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latch1.countDown();
        });
        Assert.assertTrue(latch1.await(5, TimeUnit.SECONDS));
        Assert.assertNull(errorRef.get());

        // Overwrite vertex 1 (tests delete before create)
        String output1Updated = "{\"dagElementId\":\"v1\",\"variableMap\":{\"a\":10}}";
        CountDownLatch latchOverwrite = new CountDownLatch(1);
        storageDao.storeDagVertexOutput(testAsyncId, vertexId1, output1Updated).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latchOverwrite.countDown();
        });
        Assert.assertTrue(latchOverwrite.await(5, TimeUnit.SECONDS));
        Assert.assertNull(errorRef.get());

        // Read back
        CountDownLatch latchRead = new CountDownLatch(1);
        AtomicReference<String> readContent = new AtomicReference<>();
        storageDao.getDagVertexOutput(testAsyncId, vertexId1).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            else readContent.set(res.result());
            latchRead.countDown();
        });
        Assert.assertTrue(latchRead.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(output1Updated, readContent.get());

        // Store compacted
        CountDownLatch latchCompacted = new CountDownLatch(1);
        storageDao.storeCompactedOutput(testAsyncId, childId, compacted).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latchCompacted.countDown();
        });
        Assert.assertTrue(latchCompacted.await(5, TimeUnit.SECONDS));

        // Read compacted
        CountDownLatch latchReadCompacted = new CountDownLatch(1);
        AtomicReference<String> readCompactedContent = new AtomicReference<>();
        storageDao.getCompactedOutput(testAsyncId, childId).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            else readCompactedContent.set(res.result());
            latchReadCompacted.countDown();
        });
        Assert.assertTrue(latchReadCompacted.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(compacted, readCompactedContent.get());

        // Cleanup
        CountDownLatch latchCleanup = new CountDownLatch(1);
        storageDao.cleanupAsyncId(testAsyncId).setHandler(res -> {
            if (res.failed()) errorRef.set(res.cause());
            latchCleanup.countDown();
        });
        Assert.assertTrue(latchCleanup.await(5, TimeUnit.SECONDS));
        Assert.assertNull(errorRef.get());

        // Verify cleanup deleted files
        CountDownLatch latchVerify = new CountDownLatch(1);
        AtomicReference<String> verifyDeleted = new AtomicReference<>();
        storageDao.getDagVertexOutput(testAsyncId, vertexId1).setHandler(res -> {
            if (res.succeeded()) verifyDeleted.set(res.result());
            latchVerify.countDown();
        });
        Assert.assertTrue(latchVerify.await(5, TimeUnit.SECONDS));
        Assert.assertTrue(verifyDeleted.get() == null || verifyDeleted.get().trim().isEmpty());
    }
}

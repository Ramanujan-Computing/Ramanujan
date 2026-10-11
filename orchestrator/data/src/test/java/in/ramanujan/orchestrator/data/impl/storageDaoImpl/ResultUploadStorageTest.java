package in.ramanujan.orchestrator.data.impl.storageDaoImpl;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import in.ramanujan.db.layer.enums.StorageType;
import in.ramanujan.pojo.storage.ResultUploadTarget;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.URL;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ResultUploadStorageTest {
    private final String task = UUID.randomUUID().toString();
    private final String host = UUID.randomUUID().toString();
    private final String object = "uploads/" + task + "/" + host + "/" + UUID.randomUUID();
    private Vertx vertx;
    private Storage storage;
    private GoogleCloudStorageImpl dao;

    @Before
    public void setup() {
        in.ramanujan.orchestrator.base.configuration.ConfigurationGetter.init(new io.vertx.core.json.JsonObject());
        vertx = Vertx.vertx();
        storage = mock(Storage.class);
        dao = new GoogleCloudStorageImpl() {
            @Override
            protected Storage getStorage() {
                return storage;
            }
        };
        dao.init(vertx.getOrCreateContext(), StorageType.GCP);
    }

    @After
    public void close() throws Exception {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        vertx.close(result -> closed.complete(null));
        closed.get(10, TimeUnit.SECONDS);
    }

    private <T> T await(Future<T> future) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        future.setHandler(done -> {
            if (done.succeeded()) {
                result.complete(done.result());
            } else {
                result.completeExceptionally(done.cause());
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void signsAssignmentScopedPutForExactly24Hours() throws Exception {
        when(storage.signUrl(any(BlobInfo.class), eq(24L), eq(TimeUnit.HOURS),
                any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class),
                any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                .thenReturn(new URL("https://storage.example/upload"));
        long before = System.currentTimeMillis();
        ResultUploadTarget target = await(dao.createResultUploadTarget(task, host));
        assertTrue(target.getObjectName().startsWith("uploads/" + task + "/" + host + "/"));
        assertEquals("0", target.getHeaders().get("x-goog-if-generation-match"));
        assertTrue(target.getExpiresAt() >= before + TimeUnit.HOURS.toMillis(24));
        ArgumentCaptor<BlobInfo> info = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).signUrl(info.capture(), eq(24L), eq(TimeUnit.HOURS),
                any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class),
                any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class));
        assertEquals("application/json", info.getValue().getContentType());
        assertEquals("dag_element_result", info.getValue().getBucket());
        assertFalse(target.toString().contains(target.getUploadUrl()));
    }

    @Test
    public void rejectsReferencesToAnotherAssignmentWithoutReadingStorage() {
        Future<Void> result = dao.acceptResultUpload(task, UUID.randomUUID().toString(), object, 10, "hash");
        assertTrue(result.failed());
        verifyNoInteractions(storage);
    }

    @Test
    public void localStorageKeepsInlineProtocol() throws Exception {
        LocalStorageImpl local = new LocalStorageImpl();
        assertFalse(local.supportsResultUploads());
        assertNull(await(local.createResultUploadTarget(task, host)));
    }

    private Blob source(long size, String hash) {
        Blob source = mock(Blob.class);
        when(source.getSize()).thenReturn(size);
        when(source.getMd5()).thenReturn(hash);
        when(source.getContentType()).thenReturn("application/json");
        when(source.getGeneration()).thenReturn(7L);
        when(source.getBlobId()).thenReturn(BlobId.of("dag_element_result", object, 7L));
        when(storage.get(BlobId.of("dag_element_result", object))).thenReturn(source);
        return source;
    }

    @Test
    public void verifiesObjectBeforeServerSidePromotion() throws Exception {
        source(123, "hash");
        CopyWriter copy = mock(CopyWriter.class);
        when(storage.copy(any(Storage.CopyRequest.class))).thenReturn(copy);
        await(dao.acceptResultUpload(task, host, object, 123, "hash"));
        ArgumentCaptor<Storage.CopyRequest> request = ArgumentCaptor.forClass(Storage.CopyRequest.class);
        verify(storage).copy(request.capture());
        assertEquals(task, request.getValue().getTarget().getName());
        assertEquals(object, request.getValue().getTarget().getMetadata().get("result-upload-object"));
        verify(copy).getResult();
    }

    @Test
    public void mismatchedChecksumFailsWithoutPromotion() throws Exception {
        source(123, "actual");
        try {
            await(dao.acceptResultUpload(task, host, object, 123, "incorrect"));
            fail("Expected checksum verification failure");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause().getMessage().contains("checksum"));
        }
        verify(storage, never()).copy(any(Storage.CopyRequest.class));
    }

    @Test
    public void repeatedPromotionAcceptsOnlyTheSameObject() throws Exception {
        source(123, "hash");
        when(storage.copy(any(Storage.CopyRequest.class))).thenThrow(new StorageException(412, "exists"));
        Blob existing = mock(Blob.class);
        when(existing.getMetadata()).thenReturn(Collections.singletonMap("result-upload-object", object));
        when(existing.getSize()).thenReturn(123L);
        when(existing.getMd5()).thenReturn("hash");
        when(storage.get(BlobId.of("dag_element_result", task))).thenReturn(existing);
        await(dao.acceptResultUpload(task, host, object, 123, "hash"));
        when(existing.getMetadata()).thenReturn(Collections.singletonMap("result-upload-object", "other"));
        try {
            await(dao.acceptResultUpload(task, host, object, 123, "hash"));
            fail("Expected conflicting upload rejection");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause().getMessage().contains("different result"));
        }
    }
}

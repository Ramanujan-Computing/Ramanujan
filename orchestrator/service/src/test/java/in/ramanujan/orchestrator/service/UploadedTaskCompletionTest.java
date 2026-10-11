package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.enums.Status;
import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.ramanujan.orchestrator.data.dao.AsyncTaskHostMappingDao;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class UploadedTaskCompletionTest {
    private final String taskId = UUID.randomUUID().toString();
    private final String hostId = UUID.randomUUID().toString();
    private final String object = "uploads/" + taskId + "/" + hostId + "/" + UUID.randomUUID();
    private TaskCompleteService service;
    private AsyncTaskDao tasks;
    private AsyncTaskHostMappingDao mappings;
    private StorageDao storage;
    private AsyncTask task;

    @Before
    public void setup() throws Exception {
        service = new TaskCompleteService();
        tasks = mock(AsyncTaskDao.class);
        mappings = mock(AsyncTaskHostMappingDao.class);
        storage = mock(StorageDao.class);
        inject("asyncTaskDao", tasks);
        inject("asyncTaskHostMappingDao", mappings);
        inject("storageDao", storage);
        task = new AsyncTask();
        task.setUuid(taskId);
        task.setStatus(Status.PROCESSING.getKeyName());
        when(tasks.getAsyncTask(taskId)).thenReturn(Future.succeededFuture(task));
        when(mappings.getHostForTask(taskId)).thenReturn(Future.succeededFuture(hostId));
        when(mappings.removeMapping(hostId, taskId)).thenReturn(Future.succeededFuture());
        when(tasks.update(eq(taskId), anyMap())).thenReturn(Future.succeededFuture());
    }

    private void inject(String name, Object value) throws Exception {
        Field field = TaskCompleteService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    public void completesOnlyAfterVerifiedUploadIsDurable() {
        Future<Void> accepted = Future.future();
        when(storage.acceptResultUpload(taskId, hostId, object, 10, "hash")).thenReturn(accepted);
        Future<Void> result = service.completeUploadedTask(hostId, taskId, object, 10, "hash");
        assertFalse(result.isComplete());
        verify(tasks, never()).update(anyString(), anyMap());
        accepted.complete();
        assertTrue(result.succeeded());
        verify(tasks).update(eq(taskId), anyMap());
        verify(mappings).removeMapping(hostId, taskId);
    }

    @Test
    public void missingOrCorruptUploadDoesNotCompleteTask() {
        when(storage.acceptResultUpload(taskId, hostId, object, 10, "hash"))
                .thenReturn(Future.failedFuture("checksum mismatch"));
        Future<Void> result = service.completeUploadedTask(hostId, taskId, object, 10, "hash");
        assertTrue(result.failed());
        verify(tasks, never()).update(anyString(), anyMap());
        verify(mappings, never()).removeMapping(anyString(), anyString());
    }

    @Test
    public void reassignedWorkerCannotPromoteResults() {
        when(mappings.getHostForTask(taskId)).thenReturn(Future.succeededFuture("new-host"));
        Future<Void> result = service.completeUploadedTask(hostId, taskId, object, 10, "hash");
        assertTrue(result.failed());
        verifyNoInteractions(storage);
        verify(tasks, never()).update(anyString(), anyMap());
    }

    @Test
    public void successfulCompletionRetryDoesNotRequireOriginalObject() {
        task.setStatus(Status.SUCCESS.getKeyName());
        Future<Void> result = service.completeUploadedTask(hostId, taskId, object, 10, "hash");
        assertTrue(result.succeeded());
        verifyNoInteractions(storage);
        verify(tasks, never()).update(anyString(), anyMap());
        verify(mappings).removeMapping(hostId, taskId);
    }

    @Test
    public void inlineResultsRemainSupported() throws Exception {
        when(storage.storeAsyncTaskResult(eq(taskId), any())).thenReturn(Future.succeededFuture());
        Future<Void> result = service.completeTask(hostId, taskId, java.util.Collections.singletonMap("value", 3));
        assertTrue(result.succeeded());
        verify(storage).storeAsyncTaskResult(eq(taskId), contains("\"map\""));
    }

    @Test
    public void databaseFailureDoesNotReportSuccess() {
        when(storage.acceptResultUpload(taskId, hostId, object, 10, "hash"))
                .thenReturn(Future.succeededFuture());
        when(tasks.update(eq(taskId), anyMap())).thenReturn(Future.failedFuture("database unavailable"));
        Future<Void> result = service.completeUploadedTask(hostId, taskId, object, 10, "hash");
        assertTrue(result.failed());
        verify(mappings, never()).removeMapping(anyString(), anyString());
    }
}

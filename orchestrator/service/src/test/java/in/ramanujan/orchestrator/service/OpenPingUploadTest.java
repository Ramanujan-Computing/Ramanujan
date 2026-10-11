package in.ramanujan.orchestrator.service;

import in.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.ramanujan.orchestrator.data.dao.HostsDao;
import in.ramanujan.orchestrator.data.dao.StorageDao;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.storage.ResultUploadTarget;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OpenPingUploadTest {
    private OpenPingService service;
    private HostsDao hosts;
    private StorageDao storage;

    @Before
    public void setup() throws Exception {
        service = new OpenPingService();
        hosts = mock(HostsDao.class);
        storage = mock(StorageDao.class);
        for (String name : new String[]{"hostsDao", "storageDao"}) {
            Field field = OpenPingService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, name.equals("hostsDao") ? hosts : storage);
        }
    }

    @Test
    public void assignmentWaitsForSignedUploadTarget() {
        AsyncTask task = new AsyncTask();
        task.setUuid("task");
        task.setRuleEngineInput(new RuleEngineInput());
        when(hosts.putMachineForComputation("host")).thenReturn(Future.succeededFuture(task));
        Future<ResultUploadTarget> upload = Future.future();
        when(storage.createResultUploadTarget("task", "host")).thenReturn(upload);
        Future<AsyncTask> result = service.storePing("host");
        assertFalse(result.isComplete());
        ResultUploadTarget target = new ResultUploadTarget();
        target.setObjectName("assignment/result");
        upload.complete(target);
        assertTrue(result.succeeded());
        assertSame(target, result.result().getResultUpload());
    }

    @Test
    public void signingFailureDoesNotSilentlyFallBackToInlineResults() {
        AsyncTask task = new AsyncTask();
        task.setUuid("task");
        task.setRuleEngineInput(new RuleEngineInput());
        when(hosts.putMachineForComputation("host")).thenReturn(Future.succeededFuture(task));
        when(storage.createResultUploadTarget("task", "host"))
                .thenReturn(Future.failedFuture("signing permission denied"));
        Future<AsyncTask> result = service.storePing("host");
        assertTrue(result.failed());
        assertEquals("signing permission denied", result.cause().getMessage());
    }

    @Test
    public void idleWorkersDoNotCreateUploadTargets() {
        when(hosts.putMachineForComputation("host")).thenReturn(Future.succeededFuture(null));
        assertNull(service.storePing("host").result());
        verifyNoInteractions(storage);
    }
}

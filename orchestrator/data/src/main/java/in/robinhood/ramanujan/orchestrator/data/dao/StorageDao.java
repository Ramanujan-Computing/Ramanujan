package in.robinhood.ramanujan.orchestrator.data.dao;

import in.robinhood.ramanujan.orchestrator.base.pojo.CheckpointResumePayload;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.checkpoint.Checkpoint;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

@Component
public interface StorageDao {
    public Future<Void> storeAsyncTaskResult(String asyncTaskId, Object object) throws Exception;
    public Future<Void> storeDebugResult(String asyncTaskId, Object debugObj) throws Exception;
    public Future<RuleEngineInput> getAsyncTaskRuleEngineInput(String asyncTaskId) throws Exception;
    public Future<Void> storeCheckpoint(String asyncId, Checkpoint checkpoint);
    public Future<Checkpoint> getCheckpoint(String asyncId);
    public Future<Void> storeBreakpoints(String asyncId, CheckpointResumePayload checkpointResumePayload);
    public Future<CheckpointResumePayload> getBreakpoints(String asyncId);
}

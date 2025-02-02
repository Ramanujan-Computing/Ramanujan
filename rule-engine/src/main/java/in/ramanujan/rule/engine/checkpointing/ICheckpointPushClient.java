package in.ramanujan.rule.engine.checkpointing;

import in.ramanujan.pojo.checkpoint.Checkpoint;

import java.util.concurrent.Future;

public interface ICheckpointPushClient {
    public Future<Void> call(Checkpoint checkpoint);
}

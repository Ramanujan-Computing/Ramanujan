package in.ramanujan.rule.engine.checkpointing;

import in.ramanujan.pojo.checkpoint.Checkpoint;

import java.util.concurrent.Future;

public class CheckpointPusher {
    private Long lastUsed = System.currentTimeMillis();
    private final Checkpoint checkpoint;
    private final ICheckpointPushClient checkpointPushClient;

    private Future<Void> lastPushCalled;

    public CheckpointPusher(Checkpoint checkpoint, ICheckpointPushClient checkpointPushClient) {
        this.checkpoint = checkpoint;
        this.checkpointPushClient = checkpointPushClient;
    }

    public void checkAndTrigger() {
        Long time = System.currentTimeMillis();
        if(time <= (lastUsed + 30_000L)) {
            return;
        }
        while(lastPushCalled != null && !lastPushCalled.isDone());
        if(checkpointPushClient != null) {
            lastPushCalled = checkpointPushClient.call(checkpoint);
        }
        lastUsed = time;
    }
}

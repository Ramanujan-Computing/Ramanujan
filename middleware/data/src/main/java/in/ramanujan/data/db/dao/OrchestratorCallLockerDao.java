package in.ramanujan.data.db.dao;

import io.vertx.core.Future;
import org.springframework.stereotype.Component;

@Component
public interface OrchestratorCallLockerDao {
    public Future<Void> insertLocker(String threadId, String dagElementId, Long timestamp);
    public Future<Boolean> attainedLock(String threadId, String dagElementId);
}

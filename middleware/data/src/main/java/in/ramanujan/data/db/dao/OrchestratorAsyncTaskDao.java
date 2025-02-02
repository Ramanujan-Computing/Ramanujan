package in.ramanujan.data.db.dao;

import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public interface OrchestratorAsyncTaskDao {
    public Future<Void> addMapping(final String asyncTaskId, final String orchestrationId, final String dagElementId);
    public Future<Map<String, String>> getMapping(final String asyncTaskId);
    public Future<Void> removeOrchestratorAsyncId(final String asyncId, final String dagElementId);
    public Future<Void> remove(final String asyncId);
    public Future<Boolean> isPresent(final String asyncId, final String dagElementId);
}

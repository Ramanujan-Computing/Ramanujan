package in.ramanujan.data.db.dao;

import io.vertx.core.Future;
import org.springframework.stereotype.Component;

@Component
public interface DagElementVariableDao {
    Future<Void> insertVariable(final String dagElementId, String variableId, Object value);
    Future<Void> insertArrayValue(final String dagElementId, String arrayId, String index, Object value);
}

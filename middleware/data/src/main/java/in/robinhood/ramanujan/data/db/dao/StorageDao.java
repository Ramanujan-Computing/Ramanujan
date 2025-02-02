package in.robinhood.ramanujan.data.db.dao;

import in.robinhood.ramanujan.middleware.base.UserReadableDebugPoints;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;

@Component
public interface StorageDao {
    public Future<Object> getDagElementResult(String objectId) throws Exception;
    public Future<Void> storeDagElement(String orchestratorAsyncId, RuleEngineInput ruleEngineInput) throws Exception;
    public Future<Void> storeDagElementCode(String dagElementId, String code);
    public Future<Void> storeCommonCode(String asyncId, String code);
    public Future <String> getCommonCode(String asyncId);
    public Future<UserReadableDebugPoints> getDebugPoints(String orchestratorAsyncId);
    public Future<String> getDagElementCode(String dagElementId);
}

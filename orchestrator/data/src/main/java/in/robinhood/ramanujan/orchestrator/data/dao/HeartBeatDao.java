package in.robinhood.ramanujan.orchestrator.data.dao;

import in.robinhood.ramanujan.orchestrator.base.pojo.HeartBeat;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;



@Component
public interface HeartBeatDao {
    public Future<HeartBeat> getLastHeartBeat(String hostId);
    public Future<Void> updateHeartBeat(HeartBeat heartBeat);
}

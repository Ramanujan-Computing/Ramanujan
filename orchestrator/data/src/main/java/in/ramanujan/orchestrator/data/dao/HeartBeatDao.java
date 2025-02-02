package in.ramanujan.orchestrator.data.dao;

import in.ramanujan.orchestrator.base.pojo.HeartBeat;
import io.vertx.core.Future;
import org.springframework.stereotype.Component;



@Component
public interface HeartBeatDao {
    public Future<HeartBeat> getLastHeartBeat(String hostId);
    public Future<Void> updateHeartBeat(HeartBeat heartBeat);
}

package in.ramanujan.orchestrator.data.impl.heartBeatDaoImpl;

import in.ramanujan.orchestrator.base.pojo.HeartBeat;
import in.ramanujan.orchestrator.data.dao.HeartBeatDao;
import io.vertx.core.Future;

import java.util.Map;


//@Component
public class HeartBeatDaoHashMapImpl implements HeartBeatDao {

    private Map<String, HeartBeat> heartBeatMap;

//    @PostConstruct
//    public void init() {
//        heartBeatMap = new HashMap<>();
//    }

    @Override
    public Future<HeartBeat> getLastHeartBeat(String hostId) {
        return Future.succeededFuture(heartBeatMap.get(hostId));
    }

    @Override
    public Future<Void> updateHeartBeat(HeartBeat heartBeat) {
        heartBeatMap.put(heartBeat.getHostId(), heartBeat);
        return Future.succeededFuture();
    }
}

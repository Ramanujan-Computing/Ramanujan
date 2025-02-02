package in.robinhood.ramanujan.orchestrator.data.impl.heartBeatDaoImpl;

import in.robinhood.ramanujan.db.layer.constants.Keys;
import in.robinhood.ramanujan.db.layer.enums.QueryType;
import in.robinhood.ramanujan.db.layer.schema.HostMapping;
import in.robinhood.ramanujan.db.layer.utils.QueryExecutor;
import in.robinhood.ramanujan.orchestrator.base.pojo.HeartBeat;
import in.robinhood.ramanujan.orchestrator.data.dao.HeartBeatDao;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class HeartbeatDaoSqlDbImpl implements HeartBeatDao {

    @Autowired
    private QueryExecutor queryExecutor;

    @Override
    public Future<HeartBeat> getLastHeartBeat(String hostId) {
        Future<HeartBeat> future = Future.future();
        try {
            HostMapping hostMapping = new HostMapping();
            hostMapping.setHostId(hostId);

            queryExecutor.execute(hostMapping, Keys.HOST_ID, QueryType.SELECT).setHandler(handler -> {
               if(handler.succeeded()) {
                   if(handler.result() == null || handler.result().size() == 0) {
                       future.complete();
                       return;
                   }
                   HostMapping result = (HostMapping) handler.result().get(0);
                   HeartBeat heartBeat = new HeartBeat(hostId, null, result.getLastPing());
                   future.complete(heartBeat);
               } else {
                   future.fail(handler.cause());
               }
            });
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> updateHeartBeat(HeartBeat heartBeat) {
        Future<Void> future = Future.future();
        try {
            HostMapping hostMapping = new HostMapping();
            hostMapping.setHostId(heartBeat.getHostId());
            hostMapping.setLastPing(heartBeat.getHeartBeatTimeEpoch());

            queryExecutor.execute(hostMapping, Keys.HOST_ID, QueryType.UPDATE).setHandler(handler -> {
               if(handler.succeeded()) {
                   future.complete();
               } else {
                   future.fail(handler.cause());
               }
            });

        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }
}

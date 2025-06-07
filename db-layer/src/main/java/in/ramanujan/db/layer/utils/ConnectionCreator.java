package in.ramanujan.db.layer.utils;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.mysqlclient.impl.MySQLPoolImpl;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnection;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ConnectionCreator {
    private MySQLPool mySQLPool;

    private MySQLPool getMySQLPool() {
        return mySQLPool;
    }

    MySQLConnectOptions connectOptions = new MySQLConnectOptions()
            .setPort(3306)
            .setHost("34.123.133.201")
            .setDatabase("ramanujan")
            .setUser("ramanujan_ro")
            .setPassword("ramaS@1234");

    // Pool options
    PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(50);

    private boolean isInitialized = false;
    public void init(Vertx vertx) {
    // Create the client pool
        if (isInitialized) {
            return;
        }
        mySQLPool = MySQLPool.pool(vertx, connectOptions, poolOptions);
        isInitialized = true;
    }

    public void init(Context context) {
        mySQLPool = new MySQLPoolImpl(context, false, connectOptions, poolOptions);
    }

    public Future<SqlConnection> getConnection() {
        Future<SqlConnection> future = Future.future();
        mySQLPool.getConnection(handler -> {
            if(handler.succeeded()) {
                future.complete(handler.result());
            } else {
                getConnection().setHandler(reHandler -> {
                   if(reHandler.succeeded()) {
                       future.complete(reHandler.result());
                   } else {
                       future.fail(reHandler.cause());
                   }
                });
            }
        });
        return future;
    }

}

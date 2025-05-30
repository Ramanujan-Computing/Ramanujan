package in.ramanujan.db.layer.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import in.ramanujan.db.layer.queryCreator.*;
import in.ramanujan.monitoringutils.MonitoringHandler;
import in.ramanujan.monitoringutils.StatsRecorderUtils;
import in.ramanujan.db.layer.enums.QueryType;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class QueryExecutor {

    @AllArgsConstructor
    @Data
    public static class DBConfig {
        private String jdbcUrl;
        private String username;
        private String password;
        private String dbName;
    }

    @Autowired
    ConnectionCreator connectionCreator;

    private final WhereClauseQueryCreator whereClauseQueryCreator = new WhereClauseQueryCreator();
    private final InsertQueryCreator insertQueryCreator = new InsertQueryCreator();
    private final InsertDuplicateQueryCreator insertDuplicateQueryCreator = new InsertDuplicateQueryCreator();

    HikariConfig config = new HikariConfig();

    Context context;
    DataSource dataSource;

    private final InMemQueryExecutor inMemQueryExecutor = new InMemQueryExecutor();

    private DB_TYPE dbType;

    public static enum DB_TYPE {
        GCP,
        IN_MEM;

        public static DB_TYPE getDBType(String dbType) {
            for (DB_TYPE type : DB_TYPE.values()) {
                if (type.name().equalsIgnoreCase(dbType)) {
                    return type;
                }
            }
            return null;
        }
    }

    public void init(Context context, DB_TYPE dbType, DBConfig dbConfig) {
        if(dbType == DB_TYPE.IN_MEM) {
            this.dbType = DB_TYPE.IN_MEM;
            inMemQueryExecutor.init(context);
        } else {
            this.dbType = DB_TYPE.GCP;
            this.context = context;
            config.setJdbcUrl("jdbc:mysql://" + dbConfig.getJdbcUrl() + "/" + dbConfig.getDbName());
            config.setUsername(dbConfig.getUsername());
            config.setPassword(dbConfig.getPassword());

            config.setMaximumPoolSize(50);
            dataSource = new HikariDataSource(config);
        }
    }


    public Future<List<Object>> execute(Object object, String index, QueryType queryType, List<Object>... batchOpObjectsListArray) throws Exception {
        if(dbType == DB_TYPE.IN_MEM) {
            return inMemQueryExecutor.execute(object, index, queryType, batchOpObjectsListArray);
        }

        Future<List<Object>> future = Future.future();
//        if(queryType == QueryType.UPSERT) {
//            return handlerUpsert(object, index, future);
//        }
        final List<Object> list;
        if(batchOpObjectsListArray != null && batchOpObjectsListArray.length == 1) {
            list = batchOpObjectsListArray[0];
        } else {
            list = null;
        }
        final CustomQuery customQuery = getCustomQuery(object, index, queryType, list);
        context.executeBlocking(blocking -> {
            try {
                Long connStart = new Date().toInstant().toEpochMilli();
                Connection connection = dataSource.getConnection();
                publishMetric("dbCon", connStart);
                Long stmtStart = new Date().toInstant().toEpochMilli();
                PreparedStatement statement = connection.prepareStatement(customQuery.getSql());
                publishMetric("dbStmt", stmtStart);
                int iterator = 0;
                if(customQuery.getTupleList() != null && customQuery.getTupleList().size() > 0) {
                    for(List<Object> tuple : customQuery.getTupleList()) {
                        addRow(tuple, statement);
                        if(queryType != QueryType.SELECT) {
                            statement.addBatch();;
//                            executeStmt(statement);
                        }
                    }
                    if(queryType != QueryType.SELECT) {
                        Long startBatchExec = new Date().toInstant().toEpochMilli();
                        statement.executeBatch();
                        publishMetric("dbBatch", startBatchExec);
                    }
                } else {
                    addRow(customQuery.getObjects(), statement);
                }
                final ResultSet resultSet;
                List<Object> objects = new ArrayList<>();
                if(queryType == QueryType.SELECT) {
                    resultSet = statement.executeQuery();
                    while(resultSet.next()) {
                        objects.add(RowToObjectConvertor.convert(resultSet, object));
                    }
                    resultSet.close();
                } else {
                    if(customQuery.getTupleList() == null || customQuery.getTupleList().size() == 0) {
                        executeStmt(statement);
                    }
                }
                statement.close();
                connection.close();
                blocking.complete(objects);
            } catch (Exception e) {
                blocking.fail(e);
            }
        }, false, handler -> {
            if(handler.succeeded()) {
                future.complete((List<Object>) handler.result());
            } else {
               future.fail(handler.cause());
            }
        });
//        connectionCreator.getConnection().setHandler(handler -> {
//            if(handler.succeeded()) {
//                handleConnectionCreate(handler.result(), customQuery, future, object, queryType);
//            } else {
//                future.fail(handler.cause());
//            }
//        });
        return future;
    }

    private void executeStmt(PreparedStatement statement) throws SQLException {
        Long start = new Date().toInstant().toEpochMilli();
        statement.executeUpdate();
        publishMetric("dbExec", start);
    }

    private void publishMetric(String metricName, Long startTime) {
        StatsRecorderUtils.record(metricName, new Date().toInstant().toEpochMilli() - startTime);
    }

    private void addRow(List<Object> objects, PreparedStatement statement) throws SQLException {
        int psIndex = 1;
        for(Object obj : objects) {
            statement.setObject(psIndex, obj);
            psIndex++;
        }
    }

    private Future<List<Object>> handlerUpsert(Object object, String index, Future<List<Object>> future) throws Exception {
        execute(object, index, QueryType.SELECT, null).setHandler(selectionHandler -> {
            try {
                if (selectionHandler.succeeded()) {
                    if (selectionHandler.result().size() > 0) {
                        execute(object, index, QueryType.UPDATE, null).setHandler(updateHandler -> {
                            if(updateHandler.succeeded()) {
                                future.complete();
                            } else {
                                future.fail(updateHandler.cause());
                            }
                        });
                    } else {
                        execute(object, index, QueryType.INSERT, null).setHandler(insertHandler -> {
                           if(insertHandler.succeeded()) {
                               future.complete();
                           } else {
                               future.fail(insertHandler.cause());
                           }
                        });
                    }
                } else {
                    future.fail(selectionHandler.cause());
                }
            } catch (Exception e) {
                future.fail(e);
            }
        });
        return future;
    }

    private CustomQuery getCustomQuery(Object object, String index, QueryType queryType, List<Object> batchOpObjects) throws Exception {
        CustomQuery customQuery;
        if(queryType == QueryType.INSERT) {
            customQuery = insertQueryCreator.query(object, batchOpObjects);
        } else if (queryType == QueryType.UPSERT) {
            customQuery = insertDuplicateQueryCreator.query(object, batchOpObjects);
        } else {
            customQuery = whereClauseQueryCreator.query(object, index,
                    WhereClauseQueryCreator.WhereTypeQuery.valueOf(queryType.name()));
        }
        return customQuery;
    }

    private void handleConnectionCreate(SqlConnection sqlConnection, CustomQuery customQuery, Future<List<Object>> future,
                                        Object sampleObject, QueryType queryType) {
        sqlConnection.prepare(customQuery.getSql(), new MonitoringHandler<>("prepareHandle", handler -> {
            if(handler.succeeded()) {
                handlePreparedStmt(handler.result(), customQuery, future, sampleObject, queryType, sqlConnection);
            } else {
                future.fail(handler.cause());
            }
        }));
    }

    private void handlePreparedStmt(PreparedQuery preparedQuery, CustomQuery customQuery, Future<List<Object>> future,
                                    Object sampleObject, QueryType queryType, SqlConnection sqlConnection) {
//        if(customQuery.getTupleList() != null) {
//            preparedQuery.batch(customQuery.getTupleList(), handler -> {
//                handlePrepareQueryExec(preparedQuery, future, sampleObject, queryType, sqlConnection, handler);
//            });
//            return;
//        }
        preparedQuery.execute(Tuple.tuple(customQuery.getObjects()), handler -> {
            handlePrepareQueryExec(preparedQuery, future, sampleObject, queryType, sqlConnection, handler);
        });
    }

    private static void handlePrepareQueryExec(PreparedQuery preparedQuery, Future<List<Object>> future, Object sampleObject, QueryType queryType, SqlConnection sqlConnection, AsyncResult<RowSet<Row>> handler) {
        if(handler.succeeded()) {
            preparedQuery.close();
            sqlConnection.close();
            List<Object> objects = new ArrayList<>();
            if(queryType == QueryType.SELECT) {
                for (Row row : handler.result()) {
                    try {
                        objects.add(RowToObjectConvertor.convert(row, sampleObject));
                    } catch (Exception e) {
                        future.fail(e);
                    }
                }
            }
             future.complete(objects);
        } else {
            future.fail(handler.cause());
        }
    }
}

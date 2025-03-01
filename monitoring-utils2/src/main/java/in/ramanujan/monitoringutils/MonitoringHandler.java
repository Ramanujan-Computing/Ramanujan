package in.ramanujan.monitoringutils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.Date;

public class MonitoringHandler<T> implements Handler<AsyncResult<T>> {
    private Handler<AsyncResult<T>> devHandler;
    private Long startTime;
    private Long endTime;
    private String monitoringMetricName;


    public MonitoringHandler(final String monitoringMetricName, final Handler<AsyncResult<T>> devHandler) {
        this.monitoringMetricName = monitoringMetricName;
        this.devHandler = devHandler;
        startTime = new Date().toInstant().toEpochMilli();
    }

    @Override
    public void handle(AsyncResult<T> asyncResult) {
        endTime = new Date().toInstant().toEpochMilli();
        StatsRecorderUtils.record(monitoringMetricName, endTime - startTime);
        devHandler.handle(asyncResult);
    }
}

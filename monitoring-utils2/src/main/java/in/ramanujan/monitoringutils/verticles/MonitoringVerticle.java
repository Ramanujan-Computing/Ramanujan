package in.ramanujan.monitoringutils.verticles;

import com.google.api.Metric;
import com.google.api.MonitoredResource;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.common.collect.Lists;
import com.google.monitoring.v3.*;
import com.google.protobuf.Timestamp;
import in.ramanujan.monitoringutils.StatsRecorderUtils;
import in.ramanujan.monitoringutils.metrics.DataPointSummary;
import in.ramanujan.monitoringutils.metrics.DataPoints;
import io.grpc.LoadBalancerRegistry;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.vertx.core.AbstractVerticle;
import com.google.protobuf.util.Timestamps;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MonitoringVerticle extends AbstractVerticle {

    Logger logger = LoggerFactory.getLogger(MonitoringVerticle.class);

    private final DataPreparerAndPusher dataPreparerAndPusher;


    public MonitoringVerticle(final String projectId, final String credFile, MONITORING_TYPE monitoringType) throws Exception {
        if(monitoringType == MONITORING_TYPE.GCP) {
            GoogleCredentials googleCredentials;
            LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
            googleCredentials = GoogleCredentials.fromStream(new FileInputStream(credFile))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));

            MetricServiceSettings clientSetting = null;
            MetricServiceClient metricServiceClient = null;
            try {
                clientSetting = MetricServiceSettings.newBuilder().setCredentialsProvider(() -> googleCredentials).build();
            } catch (IOException e) {
                logger.error("Error creating clientSetting for metric emit", e);
                throw new RuntimeException(e);
            }

            // Instantiates a client
            try {
                metricServiceClient = MetricServiceClient.create(clientSetting);
            } catch (IOException e) {
                logger.error("Error creating client for metric emit", e);
                if (metricServiceClient != null) {
                    metricServiceClient.close();
                }
                throw new RuntimeException(e);
            }
            dataPreparerAndPusher = new GCPMetricPusher(projectId, googleCredentials, metricServiceClient);
        } else {
            dataPreparerAndPusher = new LocalMetricPusher();
        }
    }

    @Override
    public void start(Future<Void> future) throws Exception {
        Long lastUsed = System.currentTimeMillis();
        future.complete();
        AtomicBoolean running = new AtomicBoolean(false);
        vertx.setPeriodic(60_000L, handler -> {
            if (running.get()) {
                return;
            }
            running.set(true);

            vertx.executeBlocking(blocking -> {
                try {
                    dataPreparerAndPusher.prepareDataAndEmit();
                    blocking.complete();
                } catch (Exception e) {
                    blocking.fail(e);
                }
            }, false, blockingHandler -> {
                running.set(false);
            });
        });
    }

    public static enum MONITORING_TYPE {
        LOCAL,
        GCP;

        public static MONITORING_TYPE fromString(String type) {
            if (type == null) {
                return LOCAL;
            }
            return MONITORING_TYPE.valueOf(type.toUpperCase());
        }
    }

    private interface DataPreparerAndPusher {
        public void prepareDataAndEmit() throws Exception;
    }

    private class LocalMetricPusher implements DataPreparerAndPusher {
        @Override
        public void prepareDataAndEmit() {}
    }

    @AllArgsConstructor
    private class GCPMetricPusher implements DataPreparerAndPusher {
        private String projectId;
        private GoogleCredentials googleCredentials;

        private final String instanceId = UUID.randomUUID().toString();

        private MetricServiceClient metricServiceClient;
        @Override
        public void prepareDataAndEmit() {
            // Prepares an individual data point
            TimeInterval interval =
                    TimeInterval.newBuilder()
                            .setEndTime(Timestamps.fromMillis(System.currentTimeMillis()))
                            .build();
            Map<String, DataPoints> metricDataPoints = StatsRecorderUtils.getMetricDataPoints();
            for (Map.Entry<String, DataPoints> metricDataPointsEntry : metricDataPoints.entrySet()) {
                String metricName = metricDataPointsEntry.getKey();
                DataPoints dataPoints = metricDataPointsEntry.getValue();
                DataPointSummary summary = dataPoints.getDataPointSummary();
                if (summary == null) {
                    continue;
                }

                List<Point> pointList = new ArrayList<>();
                //count
                String metricNameCount = metricName + "_count";
                pointList = new ArrayList<>();
                TypedValue value = TypedValue.newBuilder().setInt64Value(summary.count).build();
                Point point = Point.newBuilder().setInterval(interval).setValue(value).build();
                pointList.add(point);

                emitMetric(metricServiceClient, pointList, metricNameCount);

                //50 %le
                String metricNameP50 = metricName + "_p50";
                pointList = new ArrayList<>();
                value = TypedValue.newBuilder().setInt64Value(summary.percentile50).build();
                point = Point.newBuilder().setInterval(interval).setValue(value).build();
                pointList.add(point);

                emitMetric(metricServiceClient, pointList, metricNameP50);

                //90 %le
                String metricNameP90 = metricName + "_p90";
                pointList = new ArrayList<>();
                value = TypedValue.newBuilder().setInt64Value(summary.percentile90).build();
                point = Point.newBuilder().setInterval(interval).setValue(value).build();
                pointList.add(point);

                emitMetric(metricServiceClient, pointList, metricNameP90);

                //99 %le
                String metricNameP99 = metricName + "_p99";
                pointList = new ArrayList<>();
                value = TypedValue.newBuilder().setInt64Value(summary.percentile99).build();
                point = Point.newBuilder().setInterval(interval).setValue(value).build();
                pointList.add(point);

                emitMetric(metricServiceClient, pointList, metricNameP99);
            }
        }

        private void emitMetric(MetricServiceClient metricServiceClient, List<Point> pointList, String metricName) {
            logger.info("Emitting metric for " + metricName);
            try {
                ProjectName name = ProjectName.of(projectId);

// Prepares the metric descriptor
                Map<String, String> metricLabels = new HashMap<>();
                Metric metric =
                        Metric.newBuilder()
                                .setType("custom.googleapis.com/" + metricName)
                                .putAllLabels(metricLabels)
                                .build();

// Prepares the monitored resource descriptor
                Map<String, String> resourceLabels = new HashMap<>();
                resourceLabels.put("instance_id", instanceId);
                resourceLabels.put("zone", "us-central1-f");

                MonitoredResource resource =
                        MonitoredResource.newBuilder().setType("gce_instance").putAllLabels(resourceLabels).build();

// Prepares the time series request
                TimeSeries timeSeries =
                        TimeSeries.newBuilder()
                                .setMetric(metric)
                                .setResource(resource)
                                .addAllPoints(pointList)
                                .build();

                List<TimeSeries> timeSeriesList = new ArrayList<>();
                timeSeriesList.add(timeSeries);

                CreateTimeSeriesRequest request =
                        CreateTimeSeriesRequest.newBuilder()
                                .setName(name.toString())
                                .addAllTimeSeries(timeSeriesList)
                                .build();

// Writes time series data
                metricServiceClient.createTimeSeries(request);
                logger.info("Done writing time series value.");
            } catch (Exception e) {
                logger.error("Failed writing metric", e);
            }
        }
    }


}

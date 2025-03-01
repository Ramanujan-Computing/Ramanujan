package in.ramanujan.monitoringutils;

import in.ramanujan.monitoringutils.metrics.DataPoints;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatsRecorderUtils {

    private static Map<String, DataPoints> metricDataPoints = new ConcurrentHashMap<>();

    private static DataPoints getDataPointsToAdd(String metricName) {
        DataPoints dataPoints = metricDataPoints.get(metricName);
        if(dataPoints == null) {
            dataPoints = new DataPoints();
            metricDataPoints.put(metricName, dataPoints);
        }
        return dataPoints;
    }

    public static void record(String metricName, Long val) {
        getDataPointsToAdd(metricName).addRecord(val);
    }

    public static Map<String, DataPoints> getMetricDataPoints() {
        return new HashMap<String, DataPoints>(){{putAll(metricDataPoints);}};
    }
}

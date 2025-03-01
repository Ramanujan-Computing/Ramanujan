package in.ramanujan.monitoringutils.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataPoints {
    private List<Long> latencyInMillis = new ArrayList<>();

    public void addRecord(Long record) {
        synchronized (this) {
            latencyInMillis.add(record);
        }
    }

    public DataPointSummary getDataPointSummary() {
        synchronized (this) {
            DataPointSummary dataPointSummary = new DataPointSummary();
            Collections.sort(latencyInMillis);
            int count = latencyInMillis.size();
            if(count == 0) {
                return null;
            }
            dataPointSummary.count = count;
            dataPointSummary.percentile50 = latencyInMillis.get(count/2);
            dataPointSummary.percentile90 = latencyInMillis.get(count*90/100);
            dataPointSummary.percentile99 = latencyInMillis.get(count*99/100);
            latencyInMillis = new ArrayList<>();
            return dataPointSummary;
        }
    }

}

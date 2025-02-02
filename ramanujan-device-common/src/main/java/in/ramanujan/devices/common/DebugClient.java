package in.ramanujan.devices.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import in.ramanujan.debugger.UserReadableDebugPoint;
import in.ramanujan.devices.common.logging.Logger;
import in.ramanujan.devices.common.logging.LoggerFactory;
import in.ramanujan.devices.common.pojo.ResultSubmitPayload;
import in.ramanujan.rule.engine.debugger.IDebugPushClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static in.ramanujan.devices.common.OrchestratorApiCallHelper.*;

public class DebugClient implements IDebugPushClient {
    int count = 0;
    final String asyncId;

    final ObjectMapper objectMapper = new ObjectMapper();

    private String debugValuePush = "/debugValues";

    static Logger logger;

    final static ExecutorService executorService = Executors.newFixedThreadPool(16);

    static void init(final LoggerFactory loggerFactory) {
        logger = loggerFactory.getLogger(CodeExecutor.class);
    }

    public DebugClient(String asyncId) {
        this.asyncId = asyncId;
    }


    @Override
    public void call(List<UserReadableDebugPoint> list) throws IOException {
        ResultSubmitPayload.UserReadableDebugPoints userReadableDebugPoints = new ResultSubmitPayload.UserReadableDebugPoints(list);
        String debugValues = objectMapper.writeValueAsString(userReadableDebugPoints);
        int size = 1024 * 100;//100 KB block

        int counter = 0;
        List<Future> futures = new ArrayList<>();
        for (int start = 0; start < debugValues.length(); start += size) {
            final int executeCounter = counter + count;
            logger.info(asyncId + "_" + (counter + count));
            final int starter = start;
            futures.add(executorService.submit(() -> {
                submitDebugValues(asyncId + "_" + (executeCounter), debugValues.substring(starter, Math.min(debugValues.length(), starter + size)));
                logger.info("done " + executeCounter);
            }));

            counter++;
        }
        for(Future future : futures) {
            try {
                future.get();
            } catch (Exception ex) {}
        }
        count += counter;
    }

    private void submitDebugValues(String asyncId, String data) {
        try {
            Request request = new Request.Builder()
                    .post(RequestBody.create(JSON, data))
                    .url(host + debugValuePush + "?asyncId=" + asyncId)
                    .build();
            Response response = getOkHttpClient().newCall(request).execute();

            if (response.code() != 200) {
                response.body().close();
                submitDebugValues(asyncId, data);
            } else {
                response.body().close();
            }
        } catch (Exception e) {
            System.out.println("send of debug value failed for " + e);
            e.printStackTrace();
            submitDebugValues(asyncId, data);
        }
    }
}

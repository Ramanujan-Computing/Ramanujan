package in.ramanujan.devices.common;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class OrchestratorApiCallHelper {
    static OkHttpClient okHttpClient;


    static String host = "";

    static synchronized  void setHost(String host) {
        if(!host.isEmpty())
        {
            return;
        }
        OrchestratorApiCallHelper.host = host;
    }

    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");


    static OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient();
            okHttpClient.setConnectTimeout(15, TimeUnit.SECONDS);
            okHttpClient.setReadTimeout(60, TimeUnit.SECONDS);
            okHttpClient.setWriteTimeout(60, TimeUnit.SECONDS);
        }
        return okHttpClient;
    }
}

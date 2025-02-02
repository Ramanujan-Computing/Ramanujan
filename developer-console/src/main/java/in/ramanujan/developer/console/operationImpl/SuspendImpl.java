package in.ramanujan.developer.console.operationImpl;

import in.ramanujan.developer.console.Operation;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.List;

public class SuspendImpl implements Operation {
    @Override
    public void execute(List<String> args) {
        final String asyncId = args.get(0);
        OkHttpClient httpClient = new OkHttpClient();
        RequestBody requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), "");
        Request request = new Request.Builder().url("http://localhost:8888/suspend?asyncId=" + asyncId)
                .put(requestBody).build();
        try {
            httpClient.newCall(request).execute();
            System.out.println("AsyncTask " + asyncId + " successfully suspended");
        } catch (Exception e) {
            System.out.println("AsyncTask " + asyncId + " FAILED to suspend. Please try again.");
        }
    }
}

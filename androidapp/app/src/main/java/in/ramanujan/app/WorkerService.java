package in.ramanujan.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.rule.engine.RuleEngineInputProtoSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that keeps Ramanujan worker threads alive even when the
 * app is not in the foreground.  The service is started/stopped by
 * {@link MainActivity} via {@code startService} / {@code stopService}.
 *
 * A persistent notification lets Android know this process is doing user-visible
 * work, preventing it from being killed in the background.
 */
public class WorkerService extends Service {

    public static final String EXTRA_SERVER_URL = "server_url";
    public static final String ACTION_STOP      = "in.ramanujan.app.STOP_WORKERS";

    private static final String CHANNEL_ID   = "ramanujan_workers";
    private static final int    NOTIF_ID     = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExecutorService pool;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String serverUrl = intent != null
                ? intent.getStringExtra(EXTRA_SERVER_URL) : "http://localhost:8888";
        if (serverUrl == null || serverUrl.isEmpty()) serverUrl = "http://localhost:8888";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(serverUrl),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, buildNotification(serverUrl));
        }

        startWorkers(serverUrl);

        // If killed, do not restart automatically — user must tap button again
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // not a bound service
    }

    // -------------------------------------------------------------------------
    // Worker threads (same logic as MainActivity, now lives in the service)
    // -------------------------------------------------------------------------

    private void startWorkers(String serverUrl) {
        if (pool != null) pool.shutdownNow();
        int numThreads = Runtime.getRuntime().availableProcessors();
        pool = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            final String url = serverUrl;
            pool.submit(() -> workerLoop(url));
        }
        System.err.println("[WorkerService] started " + numThreads + " worker(s) → " + serverUrl);
    }

    @SuppressWarnings("unchecked")
    private void workerLoop(String serverUrl) {
        String hostId = UUID.randomUUID().toString();
        System.err.println("[WorkerService] hostId=" + hostId);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Long-poll: server blocks up to 900 ms waiting for work
                Map<String, Object> pingResp = postJson(serverUrl + "/pings/open?uuid=" + hostId, "");
                if (pingResp == null) continue;
                if (!"SUCCESS".equalsIgnoreCase((String) pingResp.get("status"))) continue;

                Object dataObj = pingResp.get("data");
                if (dataObj == null) continue; // no pending tasks

                Map<String, Object> taskData = (Map<String, Object>) dataObj;
                String uuid           = (String) taskData.get("uuid");
                String firstCommandId = (String) taskData.get("firstCommandId");
                Object reiObj         = taskData.get("ruleEngineInput");
                if (uuid == null || reiObj == null) continue;

                System.err.println("[Worker] task " + uuid + " firstCmd=" + firstCommandId);

                RuleEngineInput rei = MAPPER.convertValue(reiObj, RuleEngineInput.class);

                List<File> tempFiles = new ArrayList<>();
                try {
                    if (rei.getArrays() != null) {
                        List<Array> binaryArrays = new ArrayList<>();
                        for (Array a : rei.getArrays()) {
                            if (a.getBinaryFile() != null && !a.getBinaryFile().isEmpty()) {
                                binaryArrays.add(a);
                            }
                        }

                        if (!binaryArrays.isEmpty()) {
                            int numThreads = Math.min(32, binaryArrays.size());
                            ExecutorService downloadPool = Executors.newFixedThreadPool(numThreads);
                            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(binaryArrays.size());
                            List<File> synchedTempFiles = java.util.Collections.synchronizedList(tempFiles);
                            final String sUrl = serverUrl;
                            final List<Exception> downloadExceptions = java.util.Collections.synchronizedList(new ArrayList<>());

                            for (Array a : binaryArrays) {
                                final Array array = a;
                                downloadPool.submit(() -> {
                                    try {
                                        String serverPath = array.getBinaryFile();
                                        // Hash the serverPath to use as filename
                                        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                                        byte[] hashBytes = md.digest(serverPath.getBytes("UTF-8"));
                                        StringBuilder sb = new StringBuilder();
                                        for (byte b : hashBytes) sb.append(String.format("%02x", b));
                                        String hashStr = sb.toString();
                                        
                                        File localFile = new File(getCacheDir(), "local_rj_bin_" + hashStr + ".bin");
                                        
                                        if (localFile.exists() && localFile.length() > 0) {
                                            System.err.println("[Worker] Skipping download, binary already cached for array " + array.getName() + ": " + localFile.getAbsolutePath());
                                        } else {
                                            String downloadUrl = sUrl + "/binary/fetch?path=" + URLEncoder.encode(serverPath, "UTF-8");
                                            System.err.println("[Worker] Fetching binary parallel for array " + array.getName() + " from server path: " + serverPath);
                                            downloadFile(downloadUrl, localFile);
                                        }
                                        array.setBinaryFile(localFile.getAbsolutePath());
                                    } catch (Exception e) {
                                        System.err.println("[Worker] Parallel download error for " + array.getName() + ": " + e.getMessage());
                                        downloadExceptions.add(e);
                                    } finally {
                                        latch.countDown();
                                    }
                                });
                            }

                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw e;
                            } finally {
                                downloadPool.shutdownNow();
                            }

                            if (!downloadExceptions.isEmpty()) {
                                throw new IOException("Failed to download one or more weight arrays parallelly. First error: " + downloadExceptions.get(0).getMessage());
                            }
                        }
                    }

                    List<String> gpuKernels = new ArrayList<>();
                    if (rei.getFunctionCalls() != null) {
                        for (FunctionCall fc : rei.getFunctionCalls()) {
                            if (Boolean.TRUE.equals(fc.getIsGpu()) && fc.getId() != null) {
                                gpuKernels.add(fc.getId());
                            }
                        }
                    }
                    if (!gpuKernels.isEmpty()) {
                        System.err.println("[Worker] [GPU] " + gpuKernels.size()
                                + " GPU kernel(s): " + gpuKernels);
                    }

                    Map<String, Object> results = new HashMap<>();
                    long start = System.currentTimeMillis();
                    try {
                        NativeProcessor np = new NativeProcessor();
                        if (firstCommandId != null && !firstCommandId.isEmpty()) {
                            byte[] reiProto = RuleEngineInputProtoSerializer.serialize(rei);
                            np.process(reiProto, firstCommandId);
                            if (np.jniObject != null) results = np.jniObject;
                        }
                    } catch (Exception e) {
                        System.err.println("[Worker] execution error for " + uuid + ": " + e.getMessage());
                    }
                    long elapsed = System.currentTimeMillis() - start;

                    if (!gpuKernels.isEmpty()) {
                        System.err.println("[Worker] [GPU] run latency: " + elapsed
                                + " ms (kernels: " + gpuKernels + ")");
                    } else {
                        System.err.println("[Worker] task " + uuid + " done in " + elapsed + "ms"
                                + "  result keys=" + results.keySet());
                    }

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("uuid",   uuid);
                    payload.put("hostId", hostId);
                    payload.put("data",   results);
                    postJson(serverUrl + "/task/complete", MAPPER.writeValueAsString(payload));

                } finally {
                    // Do nothing, binary files are now cached persistently on device
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Worker] error: " + e.getMessage());
            }
        }
        System.err.println("[WorkerService] worker thread exiting hostId=" + hostId);
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ramanujan Workers",
                    NotificationManager.IMPORTANCE_LOW   // silent, no sound
            );
            channel.setDescription("Background GPU worker threads");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String serverUrl) {
        // Tap notification → reopen MainActivity
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "Stop" action in the notification
        Intent stopIntent = new Intent(this, WorkerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ramanujan Workers Running")
                .setContentText("→ " + serverUrl)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)        // cannot be swiped away
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPending)
                .build();
    }

    // -------------------------------------------------------------------------
    // HTTP helper (same as MainActivity)
    // -------------------------------------------------------------------------

    private Map<String, Object> postJson(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(120_000);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream is = (code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close();

        return MAPPER.readValue(baos.toByteArray(), Map.class);
    }

    private void downloadFile(String urlStr, File destFile) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(300_000); // 5 minutes timeout for large binary weights
        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new IOException("Server returned HTTP error code: " + code);
        }
        try (InputStream is = conn.getInputStream();
             OutputStream os = new FileOutputStream(destFile)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
        }
    }
}

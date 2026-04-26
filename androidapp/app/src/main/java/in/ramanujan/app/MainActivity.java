package in.ramanujan.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ramanujan.app.databinding.ActivityMainBinding;
import in.ramanujan.devices.common.RamanujanController;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME      = "ramanujan_prefs";
    private static final String PREF_SERVER_URL = "server_url";
    private static final String DEFAULT_SERVER  = "https://server.ramanujan.dev";
    private static final int    REQ_NOTIF_PERM  = 42;

    // ---- Logging ----

    private static class Logger1 implements in.ramanujan.devices.common.logging.Logger {
        private static final List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void info(Object o) {
            String msg = "INFO: " + o;
            logs.add(msg);
            System.out.println(msg);
        }

        @Override
        public void error(Object o, Throwable throwable) {
            String msg = "ERROR: " + o + "\n" + android.util.Log.getStackTraceString(throwable);
            logs.add(msg);
            System.out.println(msg);
        }

        static List<String> getLogs() { return new java.util.ArrayList<>(logs); }
        static void clearLogs()       { logs.clear(); }
    }

    private static class LoggerFactory implements in.ramanujan.devices.common.logging.LoggerFactory {
        @Override
        public in.ramanujan.devices.common.logging.Logger getLogger(Class aClass) {
            return new Logger1();
        }
    }

    static { System.loadLibrary("native"); }

    private ActivityMainBinding binding;

    public static final ObjectMapper MAPPER = new ObjectMapper();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Show this device's local IP so the user knows what to give a homelab server
        binding.deviceIpText.setText("Device IP: " + getLocalIp());

        // Restore previously saved server URL
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER);
        binding.serverUrlInput.setText(savedUrl);

        int threads = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    RamanujanController controller = new RamanujanController(DEFAULT_SERVER, new LoggerFactory());
                    controller.startOrchestrations();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        }

        // Start Workers: read URL, save it, launch foreground service
        binding.startWorkersButton.setOnClickListener(v -> {
                    String url = binding.serverUrlInput.getText().toString().trim();
                    if (url.isEmpty()) url = DEFAULT_SERVER;

                    prefs.edit().putString(PREF_SERVER_URL, url).apply();

                    // On Android 13+ we need POST_NOTIFICATIONS permission before the
                    // foreground service can show its persistent notification.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this,
                                android.Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                            // Store URL temporarily so we can start after permission grant
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                    .edit().putString(PREF_SERVER_URL, url).apply();
                            ActivityCompat.requestPermissions(this,
                                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                                    REQ_NOTIF_PERM);
                            return;
                        }
                    }

                    startWorkerService(url);
                });
        binding.showLogsButton.setOnClickListener(v -> {
            List<String> logs = Logger1.getLogs();
            StringBuilder sb = new StringBuilder();
            for (String log : logs) sb.append(log).append("\n");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Logs")
                    .setMessage(sb.length() > 0 ? sb.toString() : "No logs yet.")
                    .setPositiveButton("OK", null)
                    .show();
        });

        binding.clearLogsButton.setOnClickListener(v -> {
            Logger1.clearLogs();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Logs Cleared")
                    .setMessage("All logs have been cleared.")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    // -------------------------------------------------------------------------
    // Service helpers
    // -------------------------------------------------------------------------

    private void startWorkerService(String url) {
        Intent intent = new Intent(this, WorkerService.class);
        intent.putExtra(WorkerService.EXTRA_SERVER_URL, url);
        ContextCompat.startForegroundService(this, intent);

        binding.startWorkersButton.setEnabled(false);
        binding.startWorkersButton.setText("Workers running…");
        Logger1.clearLogs();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF_PERM) {
            // Start regardless — on older Android the notification permission doesn't exist
            String url = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(PREF_SERVER_URL, DEFAULT_SERVER);
            startWorkerService(url);
        }
    }

    private static String getLocalIp() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "unavailable";
    }
}

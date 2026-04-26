package in.ramanujan.app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;

import in.ramanujan.app.databinding.ActivityMainBinding;
import in.ramanujan.devices.common.RamanujanController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ramanujan_prefs";
    private static final String PREF_SERVER_URL = "server_url";
    private static final String DEFAULT_SERVER = "https://server.ramanujan.dev";

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

        // Start Workers: read URL, save it, launch orchestration threads
        binding.startWorkersButton.setOnClickListener(v -> {
            String url = binding.serverUrlInput.getText().toString().trim();
            if (url.isEmpty()) url = DEFAULT_SERVER;

            prefs.edit().putString(PREF_SERVER_URL, url).apply();

            final String serverUrl = url;
            binding.startWorkersButton.setEnabled(false);
            binding.startWorkersButton.setText("Workers running…");

            Logger1.clearLogs();

            int threads = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < threads; i++) {
                new Thread(() -> {
                    try {
                        RamanujanController controller = new RamanujanController(serverUrl, new LoggerFactory());
                        controller.startOrchestrations();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
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

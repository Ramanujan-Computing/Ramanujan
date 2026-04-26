package in.ramanujan.app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ramanujan.app.databinding.ActivityMainBinding;
import in.ramanujan.devices.common.RamanujanController;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.ramanujan.rule.engine.NativeProcessor;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        // Start Workers: read URL, save it, launch orchestration threads
        binding.startWorkersButton.setOnClickListener(v -> {
                    String url = binding.serverUrlInput.getText().toString().trim();
                    if (url.isEmpty()) url = DEFAULT_SERVER;

                    prefs.edit().putString(PREF_SERVER_URL, url).apply();

                    final String serverUrl = url;
                    binding.startWorkersButton.setEnabled(false);
                    binding.startWorkersButton.setText("Workers running…");

                    Logger1.clearLogs();


                    final String hostId = UUID.randomUUID().toString();;

                    new Thread(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                            try {
                                Thread.sleep(500L);

                                // Poll for work
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

                                // Deserialize as typed POJO so GPU flags are accessible
                                RuleEngineInput rei = MAPPER.convertValue(reiObj, RuleEngineInput.class);

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

                                // Execute via NativeProcessor (same path as Android app)
                                Map<String, Object> results = new HashMap<>();
                                long start = System.currentTimeMillis();
                                try {
                                    String reiJson = MAPPER.writeValueAsString(rei);
                                    NativeProcessor np = new NativeProcessor();
                                    if (firstCommandId != null && !firstCommandId.isEmpty()) {
                                        np.process(reiJson, firstCommandId);
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

                                // Submit results back to homelab server
                                Map<String, Object> payload = new LinkedHashMap<>();
                                payload.put("uuid",   uuid);
                                payload.put("hostId", hostId);
                                payload.put("data",   results);
                                postJson(serverUrl + "/task/complete", MAPPER.writeValueAsString(payload));

                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            } catch (Exception e) {
                                System.err.println("[Worker] error: " + e.getMessage());
                            }
                        }
                    }).start();

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

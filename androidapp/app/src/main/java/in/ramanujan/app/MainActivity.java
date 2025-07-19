package in.ramanujan.app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import in.ramanujan.app.databinding.ActivityMainBinding;

import in.ramanujan.devices.common.Credentials.Credentials;
import in.ramanujan.devices.common.RamanujanController;
import in.ramanujan.rule.engine.NativeProcessor;

public class MainActivity extends AppCompatActivity {

    private static class Logger1 implements  in.ramanujan.devices.common.logging.Logger {
        private static final java.util.List<String> logs = new java.util.ArrayList<>();

        @Override
        public void info(Object o) {
            String msg = "INFO: " + String.valueOf(o);
            logs.add(msg);
            System.out.println(msg);
        }

        @Override
        public void error(Object o, Throwable throwable) {
            String msg = "ERROR: " + String.valueOf(o) + "\n" + android.util.Log.getStackTraceString(throwable);
            logs.add(msg);
            System.out.println(msg);
            throwable.printStackTrace();
        }

        public static java.util.List<String> getLogs() {
            return new java.util.ArrayList<>(logs);
        }

        public static void clearLogs() {
            logs.clear();
        }
    }

    private static class LoggerFactory implements in.ramanujan.devices.common.logging.LoggerFactory {
        @Override
        public in.ramanujan.devices.common.logging.Logger getLogger(Class aClass) {
            return new Logger1();
        }
    }

    // Used to load the 'nativetest' library on application startup.
    static {
        System.loadLibrary("native");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String osType = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");

        try {
            // spawn a thread to run the native code
            for(int i=0;i<Runtime.getRuntime().availableProcessors();i++) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RamanujanController ramanujanController = new RamanujanController("https://server.ramanujan.dev",
                                    new LoggerFactory());
                            ramanujanController.startOrchestrations();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (Exception ex) {}

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());



        // Add log button click listener
        binding.showLogsButton.setOnClickListener(v -> {
            java.util.List<String> logs = Logger1.getLogs();
            StringBuilder sb = new StringBuilder();
            for (String log : logs) {
                sb.append(log).append("\n");
            }
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

    /**
     * A native method that is implemented by the 'nativetest' native library,
     * which is packaged with this application.
     */

}


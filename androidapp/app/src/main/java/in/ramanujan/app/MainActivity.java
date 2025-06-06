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
        @Override
        public void info(Object o) {
            System.out.println(o);
        }

        @Override
        public void error(Object o, Throwable throwable) {
            System.out.println(o);
            throwable.printStackTrace();
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
            for(int i=0;i<1;i++) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RamanujanController ramanujanController = new RamanujanController("http://35.232.220.56:8890",
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

        // Example of a call to a native method
        TextView tv = binding.sampleText;
       // tv.setText(stringFromJNI());

//        NativeProcessor nativeProcessor = new NativeProcessor();
//        nativeProcessor.process(null, "");
    }

    /**
     * A native method that is implemented by the 'nativetest' native library,
     * which is packaged with this application.
     */

}
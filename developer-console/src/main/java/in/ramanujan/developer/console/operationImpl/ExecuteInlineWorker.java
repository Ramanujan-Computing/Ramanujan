package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.Operation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.rule.engine.RuleEngineInputProtoSerializer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Local worker mode: polls a homelab server for tasks and executes them via
 * NativeProcessor on this machine.  Use this to triage whether a problem is
 * in the homelab server/orchestrator or in the Android GPU execution path.
 *
 * Usage:
 *   java -jar developer-console.jar worker [server-url] [num-threads]
 *
 * Defaults:
 *   server-url   = http://localhost:8888
 *   num-threads  = number of available CPUs
 *
 * Examples:
 *   java -jar developer-console.jar worker
 *   java -jar developer-console.jar worker http://localhost:8888 2
 *   java -jar developer-console.jar worker http://192.168.1.42:8888
 *
 * The worker continuously polls /pings/open, executes the ruleEngineInput
 * through NativeProcessor (same path as Android), and posts results to
 * /task/complete.  Press Ctrl-C to stop.
 */
public class ExecuteInlineWorker implements Operation {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object GPU_EXECUTION_LOCK = new Object();

    @Override
    public void execute(List<String> args) throws IOException {
        // args[0] = "worker", args[1] = optional url, args[2] = optional threads
        String serverUrl = "http://localhost:8888";
        int numThreads = Runtime.getRuntime().availableProcessors();

        if (args.size() >= 2) {
            String candidate = args.get(1).trim();
            if (candidate.startsWith("http")) {
                serverUrl = candidate.replaceAll("/$", "");
                if (args.size() >= 3) {
                    try { numThreads = Integer.parseInt(args.get(2).trim()); }
                    catch (NumberFormatException ignored) {}
                }
            } else {
                try { numThreads = Integer.parseInt(candidate); }
                catch (NumberFormatException ignored) {}
            }
        }

        System.out.println("LOCAL_WORKER_READY");
        System.out.println("LOCAL_WORKER_URL " + serverUrl);
        System.out.println("LOCAL_WORKER_THREADS " + numThreads);
        System.out.flush();

        final String url = serverUrl;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            pool.submit(() -> workerLoop(url));
        }

        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private void workerLoop(String serverUrl) {
        String hostId = UUID.randomUUID().toString();
        System.err.println("[Worker] started hostId=" + hostId);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Long-poll: the server blocks up to 900 ms waiting for work
                // before returning null, so no client-side sleep is needed.
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
                    NativeProcessor np = new NativeProcessor();
                    if (firstCommandId != null && !firstCommandId.isEmpty()) {
                        byte[] reiProto = RuleEngineInputProtoSerializer.serialize(rei);
                        if (gpuKernels.isEmpty()) {
                            np.process(reiProto, firstCommandId);
                        } else {
                            synchronized (GPU_EXECUTION_LOCK) {
                                np.process(reiProto, firstCommandId);
                            }
                        }
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

                // RETURN()-marked arrays too large for a JSON point-value map arrive here
                // as arrayId -> local temp file path (raw little-endian float32 bytes).
                // Upload each one's bytes directly, then strip the (worker-local, otherwise
                // meaningless) paths out of the JSON payload before reporting completion.
                Object binaryFilesObj = results.remove("binaryArrayFiles");
                if (binaryFilesObj instanceof Map) {
                    Map<String, String> binaryFiles = (Map<String, String>) binaryFilesObj;
                    for (Map.Entry<String, String> be : binaryFiles.entrySet()) {
                        String arrayId  = be.getKey();
                        String filePath = be.getValue();
                        try {
                            uploadBinaryFile(serverUrl, uuid, arrayId, filePath);
                        } catch (Exception uploadEx) {
                            System.err.println("[Worker] failed to upload binary array " + arrayId
                                    + " for task " + uuid + ": " + uploadEx.getMessage());
                        } finally {
                            new File(filePath).delete();
                        }
                    }
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
            } catch (Throwable t) {
                // Catch Throwable (not just Exception) so fatal errors like OutOfMemoryError
                // are logged instead of silently killing this thread: workerLoop() runs
                // inside pool.submit(), whose returned Future is never .get()'d, so an
                // uncaught Error here would otherwise vanish with no diagnostic output.
                System.err.println("[Worker] error: " + t);
                t.printStackTrace();
            }
        }
    }

    /** Uploads the full contents of a RETURN()-marked array as raw bytes (not JSON). */
    private void uploadBinaryFile(String serverUrl, String uuid, String arrayId, String filePath) throws Exception {
        byte[] data = readAllBytes(filePath);
        String url = serverUrl + "/orchestrator/uploadBinary?uuid=" + URLEncoder.encode(uuid, "UTF-8")
                + "&arrayId=" + URLEncoder.encode(arrayId, "UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(120_000);
        conn.setFixedLengthStreamingMode(data.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }

        int code = conn.getResponseCode();
        InputStream is = (code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (is != null) is.close();
        if (code >= 400) {
            throw new IOException("uploadBinary for arrayId=" + arrayId + " failed with HTTP " + code);
        }
    }

    private static byte[] readAllBytes(String filePath) throws IOException {
        try (InputStream is = new FileInputStream(filePath)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

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
}

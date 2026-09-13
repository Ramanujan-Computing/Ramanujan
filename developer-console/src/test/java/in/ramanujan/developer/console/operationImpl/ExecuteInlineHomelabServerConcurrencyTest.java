package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ExecuteInlineHomelabServerConcurrencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int PORT = 8899;
    private static final String DEFAULT_FAT_JAR_NAME = "developer-console-1.0-SNAPSHOT-fat.jar";

    @Test
    public void concurrentOrchestratorRunsWithRealHomelabAndWorkerSucceed() throws Exception {
        Assume.assumeTrue("Integration test runs only on Linux", isLinux());

        RunningProcess homelab = null;
        RunningProcess worker = null;
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Path workDir = Files.createTempDirectory("homelab_integration_");
        Path phi3Out = workDir.resolve("generated_tokens.csv");
        Path cfdOut = workDir.resolve("density.csv");

        try {
            Path phi3Kernel = writeKernel(workDir.resolve("phi3_simulation.txt"),
                    "var generated_tokens[2] : array;\n"
                            + "threadStart(t0) {\n"
                            + "    zero = 0;\n"
                            + "    one = 1;\n"
                            + "    generated_tokens[zero] = 101.0;\n"
                            + "    generated_tokens[one] = 202.0;\n"
                            + "}\n");

            Path cfdKernel = writeKernel(workDir.resolve("cfd_simulation.txt"),
                    "var density[3] : array;\n"
                            + "threadStart(t0) {\n"
                            + "    zero = 0;\n"
                            + "    one = 1;\n"
                            + "    two = 2;\n"
                            + "    density[zero] = 9.0;\n"
                            + "    density[one] = 8.0;\n"
                            + "    density[two] = 7.0;\n"
                            + "}\n");

            Path phi3Params = writeCsv(workDir.resolve("phi3_params.csv"), "1.0,2.0\n");
            Path cfdParams = writeCsv(workDir.resolve("cfd_params.csv"), "3.0,4.0\n");

                String jarPath = resolveFatJarPath();

            homelab = startProcess("if command -v rj >/dev/null 2>&1; then rj homelab " + PORT
                    + "; else java -jar \"" + jarPath + "\" homelab " + PORT + "; fi");
                waitForLog(homelab, "HOMELAB_READY", 30);

            worker = startProcess("if command -v rj >/dev/null 2>&1; then rj worker http://localhost:" + PORT
                    + " 2; else java -jar \"" + jarPath + "\" worker http://localhost:" + PORT + " 2; fi");
                waitForLog(worker, "LOCAL_WORKER_READY", 30);

            CountDownLatch startTogether = new CountDownLatch(1);
            Future<Void> phi3Future = exec.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                runAndDumpLikePhi3Client("http://localhost:" + PORT,
                        phi3Kernel.toString(),
                        Arrays.asList(phi3Params.toString()),
                        "generated_tokens",
                        phi3Out.toString(),
                        120);
                return null;
            });

            Future<Void> cfdFuture = exec.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                runAndDumpLikeCfdClient("http://localhost:" + PORT,
                        cfdKernel.toString(),
                        Arrays.asList(cfdParams.toString()),
                        "density",
                        cfdOut.toString(),
                        120);
                return null;
            });

            startTogether.countDown();
            phi3Future.get(180, TimeUnit.SECONDS);
            cfdFuture.get(180, TimeUnit.SECONDS);

            String phi3Csv = new String(Files.readAllBytes(phi3Out), StandardCharsets.UTF_8);
            String cfdCsv = new String(Files.readAllBytes(cfdOut), StandardCharsets.UTF_8);

                double[] phi3Actual = parseFlatCsv(phi3Csv);
                double[] cfdActual = parseFlatCsv(cfdCsv);

                Assert.assertArrayEquals("phi3 output mismatch", new double[]{101.0, 202.0}, phi3Actual, 0.0);
                Assert.assertArrayEquals("cfd output mismatch", new double[]{9.0, 8.0, 7.0}, cfdActual, 0.0);
        } finally {
            exec.shutdownNow();
            stopProcess(worker);
            stopProcess(homelab);
            deleteRecursively(workDir);
        }
    }

    private static void runAndDumpLikePhi3Client(String homelabUrl,
                                                 String kernelPath,
                                                 List<String> csvArgs,
                                                 String dumpName,
                                                 String dumpPath,
                                                 int timeoutSeconds) throws Exception {
        String requestId = submitOrchestratorRun(homelabUrl, kernelPath, csvArgs, timeoutSeconds);
        dumpArray(homelabUrl, requestId, dumpName, dumpPath);
    }

    private static void runAndDumpLikeCfdClient(String homelabUrl,
                                                String kernelPath,
                                                List<String> csvArgs,
                                                String dumpName,
                                                String dumpPath,
                                                int timeoutSeconds) throws Exception {
        String requestId = submitOrchestratorRun(homelabUrl, kernelPath, csvArgs, timeoutSeconds);
        dumpArray(homelabUrl, requestId, dumpName, dumpPath);
    }

    private static String submitOrchestratorRun(String homelabUrl,
                                                String kernelPath,
                                                List<String> csvArgs,
                                                int timeoutSeconds) throws Exception {
        List<String> args = new ArrayList<>();
        args.add(kernelPath);
        args.addAll(csvArgs);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("args", args);

        HttpResponse response = postJson(homelabUrl + "/orchestrator/run", payload,
                5000, timeoutSeconds * 1000);
        Assert.assertEquals(200, response.code);
        Map<?, ?> body = MAPPER.readValue(response.body, Map.class);
        Assert.assertEquals("SUCCESS", body.get("status"));
        Object requestId = body.get("requestId");
        Assert.assertNotNull("requestId missing in /orchestrator/run response", requestId);
        return String.valueOf(requestId);
    }

    private static void dumpArray(String homelabUrl, String requestId, String name, String path) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("name", name);
        payload.put("path", path);

        HttpResponse response = postJson(homelabUrl + "/orchestrator/dump", payload,
                5000, 30000);
        Assert.assertEquals(200, response.code);
        Map<?, ?> body = MAPPER.readValue(response.body, Map.class);
        Assert.assertEquals("SUCCESS", body.get("status"));
    }

    private static HttpResponse postJson(String url,
                                         Map<String, Object> payload,
                                         int connectTimeoutMs,
                                         int readTimeoutMs) throws Exception {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readFully(is);
        conn.disconnect();
        return new HttpResponse(status, text);
    }

    private static String readFully(InputStream is) throws IOException {
        if (is == null) return "";
        try (InputStream input = is) {
            byte[] buffer = new byte[4096];
            int read;
            StringBuilder sb = new StringBuilder();
            while ((read = input.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
    }

    private static double[] parseFlatCsv(String csv) {
        String text = csv == null ? "" : csv.trim();
        if (text.isEmpty()) {
            return new double[0];
        }
        String[] parts = text.split(",");
        double[] vals = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vals[i] = Double.parseDouble(parts[i].trim());
        }
        return vals;
    }

    private static Path writeKernel(Path kernelPath, String code) throws IOException {
        Files.write(kernelPath, code.getBytes(StandardCharsets.UTF_8));
        return kernelPath;
    }

    private static Path writeCsv(Path csvPath, String content) throws IOException {
        Files.write(csvPath, content.getBytes(StandardCharsets.UTF_8));
        return csvPath;
    }

    private static RunningProcess startProcess(String shellCommand) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("zsh", "-lc", shellCommand);
        pb.redirectErrorStream(true);
        pb.directory(resolveWorkspaceDir().toFile());
        Process process = pb.start();
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.offer(line);
                }
            } catch (IOException ignored) {
            }
        }, "proc-log-reader");
        reader.setDaemon(true);
        reader.start();
        return new RunningProcess(process, lines);
    }

    private static Path resolveWorkspaceDir() {
        String ws = System.getenv("RAMANUJAN_WS");
        if (ws != null && !ws.trim().isEmpty()) {
            return Paths.get(ws.trim());
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("linux");
    }

    private static String resolveFatJarPath() {
        String explicitJar = System.getenv("RAMANUJAN_FAT_JAR");
        if (explicitJar != null && !explicitJar.trim().isEmpty()) {
            return explicitJar.trim();
        }

        Path ws = resolveWorkspaceDir();
        Path wsJar = ws.resolve(DEFAULT_FAT_JAR_NAME);
        if (Files.exists(wsJar)) {
            return wsJar.toString();
        }

        return System.getProperty("user.home") + File.separator + "Desktop" + File.separator
                + "ws" + File.separator + "developer-console-1.0-SNAPSHOT-fat.jar";
    }

    private static void waitForLog(RunningProcess process, String token, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String line = process.lines.poll(300, TimeUnit.MILLISECONDS);
            if (line != null && line.contains(token)) {
                return;
            }
            if (!process.process.isAlive()) {
                throw new AssertionError("Process exited before readiness token: " + token);
            }
        }
        throw new AssertionError("Timed out waiting for log token: " + token);
    }

    private static void stopProcess(RunningProcess p) {
        if (p == null) return;
        p.process.destroy();
        try {
            if (!p.process.waitFor(4, TimeUnit.SECONDS)) {
                p.process.destroyForcibly();
                p.process.waitFor(4, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null) return;
        try {
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static final class HttpResponse {
        final int code;
        final String body;

        HttpResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static final class RunningProcess {
        final Process process;
        final BlockingQueue<String> lines;

        RunningProcess(Process process, BlockingQueue<String> lines) {
            this.process = process;
            this.lines = lines;
        }
    }
}

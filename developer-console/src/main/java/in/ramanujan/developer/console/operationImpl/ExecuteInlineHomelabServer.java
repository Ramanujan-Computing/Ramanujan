package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.utils.TranslateUtil;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static in.ramanujan.developer.console.operationImpl.ExecutorImpl.createJson;

/**
 * Homelab server mode: the developer console acts as the orchestration server.
 *
 * Worker machines running ramanujan device-common point their server URL at this
 * process.  They call the same REST endpoints as they would against the central
 * middleware, and this server distributes compiled DAG elements to them for
 * execution, then collects and merges the results.
 *
 * Usage:
 *   java -jar developer-console.jar homelab [port]   (default port: 8888)
 *
 * Stdin protocol (same as ExecuteInlineServer):
 *   run <kernel.py> <csv1> ...   – compile and dispatch to workers; blocks until done
 *   dump <name> [file]           – dump array result
 *   var  <name>                  – print scalar result
 *   quit                         – exit
 *
 * On startup prints:
 *   HOMELAB_READY
 *   HOMELAB_ADDRESS http://<localIp>:<port>
 *
 * After each completed run:
 *   KERNEL_DONE
 */
public class ExecuteInlineHomelabServer extends ExecuteInline {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Tasks compiled and waiting to be served to a polling worker
    private final BlockingQueue<PendingTask> taskQueue = new LinkedBlockingQueue<>();
    // Tasks served but not yet completed (keyed by uuid sent to worker)
    private final ConcurrentHashMap<String, PendingTask> inflight = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private static final class KernelRun {
        final Map<String, Variable> variableMap;
        final Map<String, Array>    arrayMap;
        final CountDownLatch        latch;

        KernelRun(Map<String, Variable> variableMap, Map<String, Array> arrayMap, int taskCount) {
            this.variableMap = variableMap;
            this.arrayMap    = arrayMap;
            this.latch       = new CountDownLatch(taskCount);
        }
    }

    private static final class PendingTask {
        final String     uuid;
        final DagElement dagElement;
        final KernelRun  kernelRun;
        final String     responseJson; // full OpenPingHttpResponse JSON to return to worker

        PendingTask(String uuid, DagElement dagElement, KernelRun kernelRun, String responseJson) {
            this.uuid         = uuid;
            this.dagElement   = dagElement;
            this.kernelRun    = kernelRun;
            this.responseJson = responseJson;
        }
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    @Override
    public void execute(List<String> args) throws IOException {
        int port = 8888;
        // args[0] is "homelab"; optional args[1] is port number
        if (args.size() >= 2) {
            try { port = Integer.parseInt(args.get(1)); } catch (NumberFormatException ignored) {}
        }

        startHttpServer(port);

        String ip = getLocalIp();
        System.out.println("HOMELAB_READY");
        System.out.println("HOMELAB_ADDRESS http://" + ip + ":" + port);
        System.out.flush();

        // stdin loop — same command set as ExecuteInlineServer
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                System.out.println("SERVER_EXIT");
                System.out.flush();
                break;
            }

            if (line.startsWith("run ")) {
                String[] parts = line.split("\\s+");
                List<String> kernelArgs = new ArrayList<>(parts.length - 1);
                for (int i = 1; i < parts.length; i++) kernelArgs.add(parts[i]);
                try {
                    dispatchToWorkers(kernelArgs);
                    System.out.println("KERNEL_DONE");
                } catch (Exception e) {
                    System.out.println("KERNEL_ERROR: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
                System.out.flush();
                continue;
            }

            // query commands (dump / var / arr) handled by parent via ExecutorImpl stores
            handleQueryCommand(line);
            System.out.flush();
        }
    }

    // -------------------------------------------------------------------------
    // Compile + dispatch
    // -------------------------------------------------------------------------

    private void dispatchToWorkers(List<String> args) throws Exception {
        long t0 = System.currentTimeMillis();

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array>    arrayMap    = new HashMap<>();

        CodeRunRequest req = createJson(args);
        String code = req.getCode();
        List<CsvInformation> csvList = req.getCsvInformationList() != null
                ? req.getCsvInformationList() : new ArrayList<>();

        Map<String, RuleEngineInput> functionCallsREI = new HashMap<>();
        ActualDebugCodeCreator debugCreator = new ActualDebugCodeCreator("", 0);

        String extractedCode;
        int    linesForFunctions;

        if (TranslateUtil.isPythonCode(code)) {
            extractedCode    = code;
            linesForFunctions = 0;
        } else {
            ExtractedCodeAndFunctionCode extracted =
                    translateUtil.extractCodeWithoutAbstractCodeDeclaration(code, functionCallsREI, debugCreator);
            for (Map.Entry<String, RuleEngineInput> e : functionCallsREI.entrySet()) {
                for (Variable v : e.getValue().getVariables()) variableMap.put(v.getId(), v);
                for (Array   a : e.getValue().getArrays())     arrayMap.put(a.getId(), a);
            }
            extractedCode     = extracted.getExtractedCode();
            linesForFunctions = debugCreator.getLine();
        }

        CodeSnippetElement firstSnippet = translateUtil.getCodeSnippets(
                extractedCode, new HashMap<>(), new HashMap<>(), new HashMap<>());

        List<DagElement> dagList   = new ArrayList<>();
        Map<String, String> dagCodeMap = new HashMap<>();
        DagElement firstDag = translateUtil.populateAllDagElements(
                firstSnippet, csvList, functionCallsREI,
                variableMap, arrayMap, dagList, dagCodeMap, linesForFunctions);

        List<DagElement> allElements = new ArrayList<>();
        allElements.add(firstDag);
        allElements.addAll(dagList);

        System.err.println("[Homelab] compiled " + args.get(0)
                + " in " + (System.currentTimeMillis() - t0) + "ms  DAG=" + allElements.size());

        // Execute DAG elements in topological order so each element sees the
        // merged results of its predecessors.  Dispatching all elements at once
        // (the old approach) sent every worker a snapshot of the initial arrays;
        // dependent kernels (gravity → feet → integrate) never saw each other's
        // outputs, so velocities stayed zero and positions never changed.
        Set<DagElement> completed = new HashSet<>();
        Deque<DagElement> readyQueue = new ArrayDeque<>();
        readyQueue.add(firstDag);

        while (completed.size() < allElements.size()) {
            if (readyQueue.isEmpty()) {
                for (DagElement el : allElements) {
                    if (!completed.contains(el) && completed.containsAll(el.getPreviousElements())) {
                        readyQueue.add(el);
                    }
                }
                if (readyQueue.isEmpty()) break; // cycle or all done
            }

            DagElement element = readyQueue.poll();
            if (completed.contains(element)) continue;

            // Build the task JSON NOW, after all predecessor results have been
            // merged into the shared arrayMap / variableMap objects that
            // element.getRuleEngineInput() references.
            String taskUuid = UUID.randomUUID().toString();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uuid",           taskUuid);
            data.put("ruleEngineInput", element.getRuleEngineInput());
            data.put("firstCommandId", element.getFirstCommandId());
            data.put("debug",          false);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("status", "SUCCESS");
            envelope.put("data",   data);
            String responseJson = MAPPER.writeValueAsString(envelope);

            KernelRun run = new KernelRun(variableMap, arrayMap, 1);
            PendingTask task = new PendingTask(taskUuid, element, run, responseJson);
            taskQueue.add(task);
            inflight.put(taskUuid, task);

            System.err.println("[Homelab] dispatched element (firstCmd=" + element.getFirstCommandId() + "), waiting…");
            run.latch.await();

            completed.add(element);
            for (DagElement next : element.getNextElements()) {
                if (!completed.contains(next) && completed.containsAll(next.getPreviousElements())) {
                    readyQueue.add(next);
                }
            }
        }

        System.err.println("[Homelab] all " + completed.size() + " element(s) done in "
                + (System.currentTimeMillis() - t0) + "ms");

        // Populate ExecutorImpl stores so dump / var / arr commands work post-run
        Map<String, Object> varStore = new HashMap<>();
        for (Variable v : variableMap.values()) varStore.put(v.getName(), v.getValue());

        Map<String, Map<String, Object>> arrStore = new HashMap<>();
        for (Array a : arrayMap.values()) {
            String id = a.getId();
            if (id.contains("func") || !id.contains("_name_")) continue;
            String name = id.split("_name_")[1];
            Map<String, Object> vals = a.getValues();
            int valCount = vals == null ? -1 : vals.size();
            System.err.println("[Homelab] setStores: array id=" + id + " name=" + name + " vals=" + valCount);
            if (vals == null) continue;
            for (Map.Entry<String, Object> e : vals.entrySet())
                arrStore.computeIfAbsent(name, k -> new HashMap<>()).put(e.getKey(), e.getValue());
        }
        System.err.println("[Homelab] setStores: arrStore keys=" + arrStore.keySet());
        ExecutorImpl.setStores(varStore, arrStore);
    }

    // -------------------------------------------------------------------------
    // HTTP server
    // -------------------------------------------------------------------------

    private void startHttpServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/pings/open",        this::handleOpenPing);
        server.createContext("/pings/heartbeat",   this::handleHeartbeat);
        server.createContext("/task/complete",     this::handleTaskComplete);
        server.createContext("/orchestrator/run",  this::handleOrchestratorRun);
        server.createContext("/orchestrator/dump", this::handleOrchestratorDump);
        server.createContext("/binary/fetch",      this::handleBinaryFetch);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.err.println("[Homelab] HTTP server listening on :" + port);
    }

    /** Orchestrator calls this to compile a kernel and dispatch to workers, blocking until done. */
    @SuppressWarnings("unchecked")
    private void handleOrchestratorRun(HttpExchange ex) throws IOException {
        byte[] body = readAllBytes(ex.getRequestBody());
        Map<String, Object> req = MAPPER.readValue(body, Map.class);
        List<String> args = (List<String>) req.get("args");
        try {
            dispatchToWorkers(args);
            sendJson(ex, 200, "{\"status\":\"SUCCESS\"}");
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "ERROR");
            err.put("message", e.getMessage() != null ? e.getMessage() : e.toString());
            sendJson(ex, 500, MAPPER.writeValueAsString(err));
        }
    }

    /** Orchestrator calls this to write a named array to a CSV file on the local filesystem. */
    private void handleOrchestratorDump(HttpExchange ex) throws IOException {
        byte[] body = readAllBytes(ex.getRequestBody());
        Map<String, Object> req = MAPPER.readValue(body, Map.class);
        String name = (String) req.get("name");
        String path = (String) req.get("path");

        System.err.println("[Homelab] dump request: name=" + name + " path=" + path
                + " storeKeys=" + ExecutorImpl.arrayStore.keySet());
        Map<String, Object> arr = ExecutorImpl.arrayStore.get(name);
        if (arr == null || arr.isEmpty()) {
            sendJson(ex, 404, "{\"status\":\"ERROR\",\"message\":\"Array not found: " + name + "\"}");
            return;
        }

        boolean is1D = true;
        int maxRow = 0, maxCol = 0;
        for (String key : arr.keySet()) {
            String[] dims = key.split("_");
            if (dims.length >= 2) {
                is1D = false;
                maxRow = Math.max(maxRow, Integer.parseInt(dims[0]));
                maxCol = Math.max(maxCol, Integer.parseInt(dims[1]));
            } else {
                maxRow = Math.max(maxRow, Integer.parseInt(dims[0]));
            }
        }

        StringBuilder csv = new StringBuilder();
        if (is1D) {
            for (int i = 0; i <= maxRow; i++) {
                if (i > 0) csv.append(',');
                Object v = arr.get(String.valueOf(i));
                csv.append(v != null ? v : "0.0");
            }
            csv.append('\n');
        } else {
            for (int r = 0; r <= maxRow; r++) {
                for (int c = 0; c <= maxCol; c++) {
                    if (c > 0) csv.append(',');
                    Object v = arr.get(r + "_" + c);
                    csv.append(v != null ? v : "0.0");
                }
                csv.append('\n');
            }
        }

        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(path),
                    csv.toString().getBytes(StandardCharsets.UTF_8));
            sendJson(ex, 200, "{\"status\":\"SUCCESS\"}");
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "ERROR");
            err.put("message", e.getMessage() != null ? e.getMessage() : e.toString());
            sendJson(ex, 500, MAPPER.writeValueAsString(err));
        }
    }

    /** Serves raw binary files from the server's filesystem to the worker. */
    private void handleBinaryFetch(HttpExchange ex) throws IOException {
        try {
            String query = ex.getRequestURI().getRawQuery();
            String path = null;
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0 && java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8").equals("path")) {
                        path = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                        break;
                    }
                }
            }

            if (path == null || path.isEmpty()) {
                sendJson(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Missing path parameter\"}");
                return;
            }

            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                sendJson(ex, 404, "{\"status\":\"ERROR\",\"message\":\"File not found: " + path + "\"}");
                return;
            }

            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.sendResponseHeaders(200, file.length());
            try (InputStream is = new FileInputStream(file);
                 OutputStream os = ex.getResponseBody()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
            }
        } catch (Exception e) {
            sendJson(ex, 500, "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /** Workers poll this to receive work.
     *  Long-polls up to 900 ms so the worker's HTTP connection blocks here
     *  rather than the worker sleeping between rapid fire empty polls.
     *  Returns null data only if no task arrives within the window.
     */
    private void handleOpenPing(HttpExchange ex) throws IOException {
        consumeBody(ex);
        PendingTask task;
        try {
            task = taskQueue.poll(900, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task = null;
        }
        String body = task != null
                ? task.responseJson
                : "{\"status\":\"SUCCESS\",\"data\":null}";
        sendJson(ex, 200, body);
    }

    /** Workers ping heartbeat; just acknowledge. */
    private void handleHeartbeat(HttpExchange ex) throws IOException {
        consumeBody(ex);
        sendJson(ex, 200, "{\"status\":\"SUCCESS\"}");
    }

    /** Workers submit results here. Merge results then count down the latch. */
    @SuppressWarnings("unchecked")
    private void handleTaskComplete(HttpExchange ex) throws IOException {
        // Java 8 compatible way to read request body
        byte[] body = readAllBytes(ex.getRequestBody());
        sendJson(ex, 200, "{\"status\":\"SUCCESS\"}");

        // Process asynchronously so we don't hold the worker's HTTP connection
        new Thread(() -> {
            try {
                Map<String, Object> payload = MAPPER.readValue(body, Map.class);
                String             uuid    = (String) payload.get("uuid");
                Map<String, Object> results = (Map<String, Object>) payload.get("data");

                PendingTask task = inflight.remove(uuid);
                if (task == null) {
                    System.err.println("[Homelab] received unknown uuid: " + uuid);
                    return;
                }
                mergeResults(results, task.kernelRun.variableMap, task.kernelRun.arrayMap);
                task.kernelRun.latch.countDown();
                System.err.println("[Homelab] task " + uuid + " done ("
                        + task.kernelRun.latch.getCount() + " remaining)");
            } catch (Exception e) {
                System.err.println("[Homelab] error on task/complete: " + e.getMessage());
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Result merging — mirrors executeDagElement post-processing in ExecuteInline
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void mergeResults(Map<String, Object> results,
                               Map<String, Variable> variableMap,
                               Map<String, Array>    arrayMap) {
        if (results == null) return;
        for (Map.Entry<String, Object> entry : results.entrySet()) {
            String key   = entry.getKey();
            Object value = entry.getValue();
            if ("arrayIndex".equalsIgnoreCase(key)) {
                Map<String, Map<String, Object>> arrResults = (Map<String, Map<String, Object>>) value;
                System.err.println("[Homelab] mergeResults: " + arrResults.size() + " array(s) in result; arrayMap has " + arrayMap.size() + " entries");
                for (Map.Entry<String, Map<String, Object>> ae : arrResults.entrySet()) {
                    Array array = arrayMap.get(ae.getKey());
                    if (array == null) {
                        System.err.println("[Homelab] mergeResults: no array for id=" + ae.getKey() + " (keys: " + arrayMap.keySet() + ")");
                        continue;
                    }
                    if (array.getValues() == null) {
                        array.setValues(new HashMap<>());
                    }
                    array.getValues().putAll(ae.getValue());
                    System.err.println("[Homelab] mergeResults: merged " + ae.getValue().size() + " entries into array id=" + ae.getKey());
                }
            } else {
                Variable variable = variableMap.get(key);
                if (variable != null) variable.setValue(value);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void consumeBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            byte[] buf = new byte[4096];
            while (is.read(buf) != -1) { /* drain */ }
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toByteArray();
    }

    private static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {}
        return "127.0.0.1";
    }

    /** Query command handler (dump/var/arr) delegated from the stdin loop. */
    private void handleQueryCommand(String line) {
        if (line.startsWith("var ")) {
            String[] p = line.split(" ", 2);
            if (p.length == 2) {
                Object v = ExecutorImpl.variableStore.get(p[1]);
                System.out.println(v != null ? p[1] + " = " + v : "Variable not found.");
            }
            return;
        }
        if (line.startsWith("arr ")) {
            String[] p = line.split(" ");
            if (p.length == 3) {
                Map<String, Object> arr = ExecutorImpl.arrayStore.get(p[1]);
                if (arr != null && arr.containsKey(p[2]))
                    System.out.println(p[1] + "[" + p[2] + "] = " + arr.get(p[2]));
                else
                    System.out.println("Array or index not found.");
            }
            return;
        }
        if (line.startsWith("dump ")) {
            // reuse ExecutorImpl dump via ExecuteInlineServer's inherited handler if available;
            // for now forward to a simple inline impl
            String[] p = line.split(" ");
            String name = p.length >= 2 ? p[1] : null;
            if (name == null) { System.out.println("Usage: dump <arrayName> [file]"); return; }
            Map<String, Object> arr = ExecutorImpl.arrayStore.get(name);
            if (arr == null || arr.isEmpty()) { System.out.println("Array not found: " + name); return; }
            System.out.println(name + " = " + arr);
            return;
        }
        System.out.println("Unknown command: " + line);
    }
}

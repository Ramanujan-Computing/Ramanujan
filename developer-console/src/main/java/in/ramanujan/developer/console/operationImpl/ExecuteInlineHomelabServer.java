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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private static final int MAX_COMPLETED_REQUEST_STATES = 64;

    // Tasks compiled and waiting to be served to a polling worker
    private final BlockingQueue<PendingTask> taskQueue = new LinkedBlockingQueue<>();
    // Tasks served but not yet completed (keyed by uuid sent to worker)
    private final ConcurrentHashMap<String, PendingTask> inflight = new ConcurrentHashMap<>();
    // Completed run outputs keyed by orchestrator requestId.
    private final ConcurrentHashMap<String, CompletedRunState> completedRunStates = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> completedRunOrder = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> latestRequestId = new AtomicReference<>();
    private final ReadWriteLock runStateLock = new ReentrantReadWriteLock();
    private HttpServer server;

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private static final class KernelRun {
        final Map<String, Variable> variableMap;
        final Map<String, Array>    arrayMap;
        final List<DagElement>      allElements;
        final Set<DagElement>       completedElements = Collections.newSetFromMap(new ConcurrentHashMap<DagElement, Boolean>());
        final Set<DagElement>       enqueuedElements  = Collections.newSetFromMap(new ConcurrentHashMap<DagElement, Boolean>());
        final CountDownLatch        latch;
        // arrayId -> local file path of a raw float32 binary file uploaded by a worker
        // via /orchestrator/uploadBinary, for RETURN()-marked arrays too large to
        // ship efficiently as a JSON point-value map.
        final Map<String, String>   binaryArrayFiles = new ConcurrentHashMap<>();
        volatile Throwable          failure = null;

        KernelRun(Map<String, Variable> variableMap, Map<String, Array> arrayMap, List<DagElement> allElements) {
            this.variableMap      = variableMap;
            this.arrayMap         = arrayMap;
            this.allElements      = allElements;
            this.latch            = new CountDownLatch(allElements.size());
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

    private static final class CompletedRunState {
        final Map<String, Object> variableStore;
        final Map<String, Map<String, Object>> arrayStore;
        final Map<String, List<Integer>> arrayDimensions;
        final Map<String, String> binaryArrayFileStore;

        CompletedRunState(Map<String, Object> variableStore,
                          Map<String, Map<String, Object>> arrayStore,
                          Map<String, List<Integer>> arrayDimensions,
                          Map<String, String> binaryArrayFileStore) {
            this.variableStore = variableStore;
            this.arrayStore = arrayStore;
            this.arrayDimensions = arrayDimensions;
            this.binaryArrayFileStore = binaryArrayFileStore;
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
                    dispatchToWorkers(kernelArgs, null);
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
    // Compile + dynamic DAG orchestrator dispatch
    // -------------------------------------------------------------------------

    private void dispatchElement(KernelRun run, DagElement element) {
        if (!run.enqueuedElements.add(element)) {
            return;
        }

        // Fast-path: empty elements without commands (e.g. DAG join/fork placeholder nodes)
        if (element.getFirstCommandId() == null || element.getFirstCommandId().isEmpty()) {
            System.err.println("[Homelab] element " + element.getId() + " (empty firstCommandId) completed immediately");
            markCompletedAndTriggerNext(run, element);
            return;
        }

        try {
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

            PendingTask task = new PendingTask(taskUuid, element, run, responseJson);
            inflight.put(taskUuid, task);
            taskQueue.add(task);

            System.err.println("[Homelab] dispatched task (firstCmd=" + element.getFirstCommandId()
                    + ", uuid=" + taskUuid + ") | queued=" + taskQueue.size()
                    + ", inflight=" + inflight.size());
        } catch (Exception e) {
            System.err.println("[Homelab] error dispatching element " + element.getId() + ": " + e.getMessage());
            run.failure = e;
            run.latch.countDown();
        }
    }

    private void markCompletedAndTriggerNext(KernelRun run, DagElement completedElement) {
        if (run.completedElements.add(completedElement)) {
            run.latch.countDown();
            System.err.println("[Homelab] element " + completedElement.getId()
                    + " (firstCmd=" + completedElement.getFirstCommandId() + ") done | remaining="
                    + run.latch.getCount());
        }

        List<DagElement> nextElements = completedElement.getNextElements();
        if (nextElements != null) {
            for (DagElement next : nextElements) {
                checkAndDispatchIfReady(run, next);
            }
        }
    }

    private void checkAndDispatchIfReady(KernelRun run, DagElement candidate) {
        if (run.completedElements.contains(candidate) || run.enqueuedElements.contains(candidate)) {
            return;
        }

        List<DagElement> prevs = candidate.getPreviousElements();
        boolean allSatisfied = true;
        if (prevs != null && !prevs.isEmpty()) {
            for (DagElement prev : prevs) {
                if (!run.completedElements.contains(prev)) {
                    allSatisfied = false;
                    break;
                }
            }
        }

        if (allSatisfied) {
            dispatchElement(run, candidate);
        }
    }

    protected void dispatchToWorkers(List<String> args, String requestId) throws Exception {
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

        Set<DagElement> uniqueElements = new LinkedHashSet<>();
        uniqueElements.add(firstDag);
        uniqueElements.addAll(dagList);
        List<DagElement> allElements = new ArrayList<>(uniqueElements);

        System.err.println("[Homelab] compiled " + args.get(0)
                + " in " + (System.currentTimeMillis() - t0) + "ms  DAG=" + allElements.size());

        KernelRun run = new KernelRun(variableMap, arrayMap, allElements);

        // Identify all root DAG elements (elements with no previous dependencies)
        List<DagElement> roots = new ArrayList<>();
        for (DagElement el : allElements) {
            if (el.getPreviousElements() == null || el.getPreviousElements().isEmpty()) {
                roots.add(el);
            }
        }
        if (roots.isEmpty()) {
            roots.add(firstDag);
        }

        System.err.println("[Homelab] launching DAG with " + roots.size() + " initial root element(s)...");
        synchronized (run) {
            for (DagElement root : roots) {
                dispatchElement(run, root);
            }
        }

        run.latch.await();

        if (run.failure != null) {
            throw new RuntimeException("Kernel execution failed on worker", run.failure);
        }

        System.err.println("[Homelab] all " + allElements.size() + " element(s) done in "
                + (System.currentTimeMillis() - t0) + "ms");

        // Populate ExecutorImpl stores so dump / var / arr commands work post-run
        Map<String, Object> varStore = new HashMap<>();
        for (Variable v : variableMap.values()) varStore.put(v.getName(), v.getValue());

        Map<String, Map<String, Object>> arrStore = new HashMap<>();
        Map<String, List<Integer>> arrDimensions = new HashMap<>();
        for (Array a : arrayMap.values()) {
            String id = a.getId();
            if (id.contains("func") || !id.contains("_name_")) continue;
            String name = id.split("_name_")[1];
            if (a.getDimension() != null && !a.getDimension().isEmpty()) {
                arrDimensions.put(name, new ArrayList<>(a.getDimension()));
            }
            Map<String, Object> vals = a.getValues();
            int valCount = vals == null ? -1 : vals.size();
            System.err.println("[Homelab] setStores: array id=" + id + " name=" + name + " vals=" + valCount);
            if (vals == null) continue;
            for (Map.Entry<String, Object> e : vals.entrySet())
                arrStore.computeIfAbsent(name, k -> new HashMap<>()).put(e.getKey(), e.getValue());
        }
        System.err.println("[Homelab] setStores: arrStore keys=" + arrStore.keySet());

        Map<String, String> binaryStore = new HashMap<>();
        for (Map.Entry<String, String> e : run.binaryArrayFiles.entrySet()) {
            String id = e.getKey();
            if (id.contains("func") || !id.contains("_name_")) continue;
            String name = id.split("_name_")[1];
            binaryStore.put(name, e.getValue());
        }
        System.err.println("[Homelab] setStores: binaryStore keys=" + binaryStore.keySet());
        completeRunState(requestId, varStore, arrStore, arrDimensions, binaryStore);
    }

    protected void completeRunState(String requestId,
                                    Map<String, Object> variableStore,
                                    Map<String, Map<String, Object>> arrayStore,
                                    Map<String, List<Integer>> arrayDimensions,
                                    Map<String, String> binaryArrayFileStore) {
        Map<String, Object> varCopy = new HashMap<>();
        if (variableStore != null) varCopy.putAll(variableStore);
        Map<String, Map<String, Object>> arrCopy = deepCopyArrayStore(arrayStore);
        Map<String, List<Integer>> dimCopy = arrayDimensions != null ? new HashMap<>(arrayDimensions) : new HashMap<>();
        Map<String, String> binaryCopy = new HashMap<>();
        if (binaryArrayFileStore != null) binaryCopy.putAll(binaryArrayFileStore);

        runStateLock.writeLock().lock();
        try {
            // Preserve existing interactive query behavior for stdin clients.
            ExecutorImpl.setStores(varCopy, arrCopy);

            if (requestId == null || requestId.trim().isEmpty()) {
                ExecutorImpl.setBinaryArrayFileStore(binaryCopy);
                return;
            }

            CompletedRunState previous = completedRunStates.put(requestId, new CompletedRunState(varCopy, arrCopy, dimCopy, binaryCopy));
            if (previous == null) {
                completedRunOrder.add(requestId);
            }
            latestRequestId.set(requestId);
            evictOldCompletedStatesIfNeeded();
        } finally {
            runStateLock.writeLock().unlock();
        }
    }

    private void evictOldCompletedStatesIfNeeded() {
        while (completedRunStates.size() > MAX_COMPLETED_REQUEST_STATES) {
            String oldestId = completedRunOrder.poll();
            if (oldestId == null) return;
            CompletedRunState removed = completedRunStates.remove(oldestId);
            if (removed != null) {
                deleteBinaryFiles(removed.binaryArrayFileStore.values());
            }
        }
    }

    private static Map<String, Map<String, Object>> deepCopyArrayStore(Map<String, Map<String, Object>> arrayStore) {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        if (arrayStore == null) return copy;
        for (Map.Entry<String, Map<String, Object>> e : arrayStore.entrySet()) {
            Map<String, Object> values = new HashMap<>();
            if (e.getValue() != null) values.putAll(e.getValue());
            copy.put(e.getKey(), values);
        }
        return copy;
    }

    private static void deleteBinaryFiles(Collection<String> paths) {
        for (String path : paths) {
            if (path == null) continue;
            try { new File(path).delete(); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // HTTP server
    // -------------------------------------------------------------------------

    protected void startHttpServer(int port) throws IOException {
        HttpServer createdServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.server = createdServer;
        createdServer.createContext("/pings/open",        this::handleOpenPing);
        createdServer.createContext("/pings/heartbeat",   this::handleHeartbeat);
        createdServer.createContext("/task/complete",     this::handleTaskComplete);
        createdServer.createContext("/orchestrator/run",  this::handleOrchestratorRun);
        createdServer.createContext("/orchestrator/dump", this::handleOrchestratorDump);
        createdServer.createContext("/binary/fetch",      this::handleBinaryFetch);
        createdServer.createContext("/orchestrator/uploadBinary", this::handleUploadBinary);
        createdServer.setExecutor(Executors.newCachedThreadPool());
        createdServer.start();
        System.err.println("[Homelab] HTTP server listening on :" + port);
    }

    protected void stopHttpServer() {
        HttpServer runningServer = this.server;
        if (runningServer != null) {
            runningServer.stop(0);
            this.server = null;
        }
    }

    /** Orchestrator calls this to compile a kernel and dispatch to workers, blocking until done. */
    @SuppressWarnings("unchecked")
    private void handleOrchestratorRun(HttpExchange ex) throws IOException {
        byte[] body = readAllBytes(ex.getRequestBody());
        Map<String, Object> req = MAPPER.readValue(body, Map.class);
        List<String> args = (List<String>) req.get("args");
        String requestId = req.get("requestId") != null
                ? String.valueOf(req.get("requestId"))
                : UUID.randomUUID().toString();
        try {
            dispatchToWorkers(args, requestId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("requestId", requestId);
            sendJson(ex, 200, MAPPER.writeValueAsString(response));
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "ERROR");
            err.put("requestId", requestId);
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
        String requestId = req.get("requestId") != null ? String.valueOf(req.get("requestId")) : null;

        CompletedRunState runState = resolveStateForDump(requestId);
        if (runState == null) {
            sendJson(ex, 404, "{\"status\":\"ERROR\",\"message\":\"No completed run state found\"}");
            return;
        }

        String binaryFile = runState.binaryArrayFileStore.get(name);
        if (binaryFile != null) {
            System.err.println("[Homelab] dump request: name=" + name + " path=" + path
                    + " (binary-backed, file=" + binaryFile + ")");
            try {
                writeBinaryFileAsCsv(binaryFile, path);
                sendJson(ex, 200, "{\"status\":\"SUCCESS\"}");
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "ERROR");
                err.put("message", e.getMessage() != null ? e.getMessage() : e.toString());
                sendJson(ex, 500, MAPPER.writeValueAsString(err));
            }
            return;
        }

        System.err.println("[Homelab] dump request: name=" + name + " path=" + path
        + " storeKeys=" + runState.arrayStore.keySet()
        + (requestId != null ? " requestId=" + requestId : " requestId=<latest>"));
    Map<String, Object> arr = runState.arrayStore.get(name);
        if (arr == null || arr.isEmpty()) {
            sendJson(ex, 404, "{\"status\":\"ERROR\",\"message\":\"Array not found: " + name + "\"}");
            return;
        }

        List<Integer> declaredDims = runState.arrayDimensions != null ? runState.arrayDimensions.get(name) : null;
        boolean is1D = true;
        int maxRow = 0, maxCol = 0;
        if (declaredDims != null && !declaredDims.isEmpty()) {
            if (declaredDims.size() == 1) {
                is1D = true;
                maxRow = Math.max(0, declaredDims.get(0) - 1);
            } else if (declaredDims.size() >= 2) {
                is1D = false;
                maxRow = Math.max(0, declaredDims.get(0) - 1);
                maxCol = Math.max(0, declaredDims.get(1) - 1);
            }
        } else {
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

    private CompletedRunState resolveStateForDump(String requestId) {
        runStateLock.readLock().lock();
        try {
            if (requestId != null && !requestId.trim().isEmpty()) {
                return completedRunStates.get(requestId);
            }

            String latest = latestRequestId.get();
            if (latest != null) {
                CompletedRunState latestState = completedRunStates.get(latest);
                if (latestState != null) return latestState;
            }

            // Legacy fallback for old clients that do not send requestId.
            Map<String, Object> varCopy = new HashMap<>();
            varCopy.putAll(ExecutorImpl.variableStore);
            Map<String, Map<String, Object>> arrCopy = deepCopyArrayStore(ExecutorImpl.arrayStore);
            Map<String, String> binaryCopy = new HashMap<>();
            binaryCopy.putAll(ExecutorImpl.binaryArrayFileStore);
            if (varCopy.isEmpty() && arrCopy.isEmpty() && binaryCopy.isEmpty()) {
                return null;
            }
            return new CompletedRunState(varCopy, arrCopy, Collections.emptyMap(), binaryCopy);
        } finally {
            runStateLock.readLock().unlock();
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

    /**
     * Workers POST here (raw octet-stream body) to deliver the full contents of a
     * RETURN()-marked array as a binary float32 file, instead of a JSON point-value
     * map. Query params: uuid (task uuid), arrayId (DAG-scoped array id).
     */
    private void handleUploadBinary(HttpExchange ex) throws IOException {
        try {
            String query = ex.getRequestURI().getRawQuery();
            String uuid = null, arrayId = null;
            if (query != null) {
                for (String pair : query.split("&")) {
                    int idx = pair.indexOf('=');
                    if (idx <= 0) continue;
                    String k = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    if ("uuid".equals(k)) uuid = v;
                    else if ("arrayId".equals(k)) arrayId = v;
                }
            }

            if (uuid == null || arrayId == null) {
                consumeBody(ex);
                sendJson(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Missing uuid or arrayId\"}");
                return;
            }

            PendingTask task = inflight.get(uuid);
            if (task == null) {
                consumeBody(ex);
                sendJson(ex, 404, "{\"status\":\"ERROR\",\"message\":\"Unknown task uuid: " + uuid + "\"}");
                return;
            }

            File dest = File.createTempFile("ramanujan_homelab_recv_", ".bin");
            try (InputStream is = ex.getRequestBody();
                 OutputStream os = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            }

            task.kernelRun.binaryArrayFiles.put(arrayId, dest.getAbsolutePath());
            sendJson(ex, 200, "{\"status\":\"SUCCESS\"}");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /** Converts a raw little-endian float32 binary file into a single flat comma-separated
     *  CSV line, matching the format written by write_flat_csv()/read by read_flat_csv() on
     *  the python client side. Avoids ever materializing a per-element Map/JSON structure. */
    private static void writeBinaryFileAsCsv(String binFilePath, String csvOutPath) throws IOException {
        byte[] raw = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(binFilePath));
        java.nio.FloatBuffer floats = java.nio.ByteBuffer.wrap(raw)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer();
        int n = floats.remaining();
        StringBuilder sb = new StringBuilder(Math.max(16, n * 12));
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(floats.get(i));
        }
        sb.append('\n');
        java.nio.file.Files.write(java.nio.file.Paths.get(csvOutPath),
                sb.toString().getBytes(StandardCharsets.UTF_8));
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

    /** Workers submit results here. Merge results, mark element completed, and dispatch newly ready successors. */
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
                synchronized (task.kernelRun) {
                    mergeResults(results, task.kernelRun.variableMap, task.kernelRun.arrayMap);
                    markCompletedAndTriggerNext(task.kernelRun, task.dagElement);
                }
            } catch (Exception e) {
                System.err.println("[Homelab] error on task/complete: " + e.getMessage());
                e.printStackTrace(System.err);
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
        if (line.startsWith("dump_diff ")) {
            String[] p = line.split(" ");
            if (p.length < 4) {
                System.out.println("Usage: dump_diff <arrayName> <startIdx> <endIdxInclusive> [outputFile]");
                return;
            }
            String name = p[1];
            long startIdx, endIdx;
            try {
                startIdx = Long.parseLong(p[2]);
                endIdx   = Long.parseLong(p[3]);
            } catch (NumberFormatException nfe) {
                System.out.println("dump_diff: startIdx/endIdx must be integers");
                return;
            }
            String outFile = p.length >= 5 ? p[4] : null;
            Map<String, Object> arr = ExecutorImpl.arrayStore.get(name);
            if (arr == null) {
                System.out.println("Array not found: " + name);
                return;
            }
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Map.Entry<String, Object> e : arr.entrySet()) {
                String key = e.getKey();
                long flat;
                int us = key.indexOf('_');
                try {
                    if (us < 0) {
                        flat = Long.parseLong(key);
                    } else {
                        sb.append(key).append(',').append(e.getValue()).append('\n');
                        count++;
                        continue;
                    }
                } catch (NumberFormatException nfe) {
                    continue;
                }
                if (flat >= startIdx && flat <= endIdx) {
                    sb.append(key).append(',').append(e.getValue()).append('\n');
                    count++;
                }
            }
            if (outFile != null) {
                try {
                    java.nio.file.Files.write(java.nio.file.Paths.get(outFile),
                            sb.toString().getBytes(StandardCharsets.UTF_8));
                    System.out.println("Dumped diff " + name + " (" + count + ") to " + outFile);
                } catch (Exception ex) {
                    System.out.println("Error writing file: " + ex.getMessage());
                }
            } else {
                System.out.print(sb.toString());
                System.out.println("Dumped diff " + name + " (" + count + ")");
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

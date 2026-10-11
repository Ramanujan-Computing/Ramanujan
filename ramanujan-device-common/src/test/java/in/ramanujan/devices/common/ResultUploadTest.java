package in.ramanujan.devices.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import in.ramanujan.devices.common.logging.Logger;
import in.ramanujan.pojo.storage.ResultUploadTarget;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ResultUploadTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private Orchestrator orchestrator() {
        return new Orchestrator(type -> new Logger() {
            public void info(Object message) {}
            public void error(Object message, Throwable error) {
                throw new AssertionError(String.valueOf(message), error);
            }
        });
    }

    private void submit(Orchestrator orchestrator, CodeExecutor.ProcessorFutureMap result,
                        ResultUploadTarget target) throws Exception {
        Method method = Orchestrator.class.getDeclaredMethod("submitResults",
                CodeExecutor.ProcessorFutureMap.class, String.class, String.class, ResultUploadTarget.class);
        method.setAccessible(true);
        method.invoke(orchestrator, result, "task", "host", target);
    }

    private byte[] read(InputStream stream) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) >= 0) {
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    @Test
    public void largeResultGoesToStorageWhileCompletionStaysSmall() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> digest = new AtomicReference<>();
        AtomicReference<Long> uploadedBytes = new AtomicReference<>();
        AtomicReference<JsonNode> completion = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        server.createContext("/upload", exchange -> {
            try {
                assertEquals("PUT", exchange.getRequestMethod());
                assertEquals("0", exchange.getRequestHeaders().getFirst("x-goog-if-generation-match"));
                assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[8192];
                long size = 0;
                int count;
                while ((count = exchange.getRequestBody().read(buffer)) >= 0) {
                    md5.update(buffer, 0, count);
                    size += count;
                }
                uploadedBytes.set(size);
                digest.set(Base64.getEncoder().encodeToString(md5.digest()));
            } catch (Throwable error) {
                failure.set(error);
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/task/complete", exchange -> {
            try {
                byte[] body = read(exchange.getRequestBody());
                assertTrue(body.length < 1024);
                completion.set(mapper.readTree(body));
            } catch (Throwable error) {
                failure.set(error);
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        String originalHost = OrchestratorApiCallHelper.host;
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            OrchestratorApiCallHelper.setHost(base);
            ResultUploadTarget target = new ResultUploadTarget();
            target.setUploadUrl(base + "/upload");
            target.setObjectName("assignment/result");
            target.setExpiresAt(System.currentTimeMillis() + 60000);
            target.setHeaders(Collections.singletonMap("x-goog-if-generation-match", "0"));
            char[] large = new char[33 * 1024 * 1024];
            Arrays.fill(large, 'x');
            CodeExecutor.ProcessorFutureMap result = new CodeExecutor.ProcessorFutureMap();
            result.setResult(Collections.singletonMap("large", new String(large)));
            submit(orchestrator(), result, target);
            assertNull(failure.get());
            assertTrue(uploadedBytes.get() > 32 * 1024 * 1024);
            assertFalse(completion.get().has("data"));
            assertEquals(target.getObjectName(), completion.get().get("resultObject").asText());
            assertEquals(uploadedBytes.get().longValue(), completion.get().get("resultSize").asLong());
            assertEquals(digest.get(), completion.get().get("resultMd5").asText());
        } finally {
            server.stop(0);
            OrchestratorApiCallHelper.setHost(originalHost);
        }
    }

    @Test
    public void localExecutionKeepsInlineResultPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<JsonNode> completion = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        server.createContext("/task/complete", exchange -> {
            try {
                completion.set(mapper.readTree(read(exchange.getRequestBody())));
            } catch (Throwable error) {
                failure.set(error);
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        String originalHost = OrchestratorApiCallHelper.host;
        try {
            OrchestratorApiCallHelper.setHost("http://127.0.0.1:" + server.getAddress().getPort());
            CodeExecutor.ProcessorFutureMap result = new CodeExecutor.ProcessorFutureMap();
            result.setResult(Collections.singletonMap("value", 7));
            submit(orchestrator(), result, null);
            assertNull(failure.get());
            assertEquals(7, completion.get().get("data").get("value").asInt());
            assertFalse(completion.get().has("resultObject"));
        } finally {
            server.stop(0);
            OrchestratorApiCallHelper.setHost(originalHost);
        }
    }
}

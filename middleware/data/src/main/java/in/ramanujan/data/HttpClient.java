package in.ramanujan.data;

import io.vertx.core.json.JsonObject;
import sun.net.www.protocol.http.HttpURLConnection;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpClient {
    public int statusCode;
    public String response;

    public HttpClient(String method, String urlStr, JsonObject payload, Map<String, String> queryParams) throws Exception {
        StringBuilder build = new StringBuilder(urlStr);
        if (queryParams != null && queryParams.size() > 0) {
            build.append("?");
            int maxSize = queryParams.size();
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                build.append(entry.getKey()).append("=").append(entry.getValue());
                maxSize--;
                if (maxSize > 0) {
                    build.append("&");
                }
            }
        }
        URL url = new URL(build.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (payload != null) {
            String payloadStr = payload.toString();
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(payloadStr.length());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payloadStr.getBytes(StandardCharsets.UTF_8));
            }
        }
        statusCode = conn.getResponseCode();
        // Create a byte array output stream to store the data
        InputStream in = conn.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Create a buffer to read bytes from the input stream
        byte[] buffer = new byte[1024];

        // Read bytes from the input stream until there is no more data
        int len;
        while ((len = in.read(buffer)) != -1) {
            // Write the bytes to the output stream
            out.write(buffer, 0, len);
        }

        // Close the streams
        in.close();
        out.close();

        // Convert the output stream to a byte array
        byte[] data = out.toByteArray();

        // Convert the byte array to a string using UTF-8 encoding
        response = new String(data, StandardCharsets.UTF_8);
    }
}

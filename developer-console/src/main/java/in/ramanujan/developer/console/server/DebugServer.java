package in.ramanujan.developer.console.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.pojo.ApiResponse;
import in.ramanujan.developer.console.pojo.DebugResult;
import in.ramanujan.developer.console.server.html.Cell;
import in.ramanujan.developer.console.server.html.Table;
import in.ramanujan.developer.console.server.html.clickable.AddDebug;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class DebugServer {
    private final ServerSocket serverSocket;
    private final Thread serverThread;

    private final String NEXT_LINE = "/nextline";
    private final String ADD_DEBUG_POINT = "/debug/add";
    private final String REMOVE_DEBUG_POINT = "/debug/remove";
    
    private final String GET = "/debug/get";

    private final Map<String, Set<Integer>> debugLine = new HashMap<>();

    private final String asyncId;

    private final Map<String, DebugResult> dagElementDebugResultMap = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();


    public DebugServer(int port, String firstDagElementId, String asyncId) throws Exception {
        this.asyncId = asyncId;
        getDebugData(asyncId, firstDagElementId);
        serverSocket = new ServerSocket(port);
        serverThread = new Thread(() -> {
           while(true) {
               try {
                   listen();
               } catch (IOException e) {
               }
           }
        });
        serverThread.start();
    }

    private void getDebugData(String asyncId, String dagElementId) {
        OkHttpClient httpClient = new OkHttpClient();

        Request request = new Request.Builder().url("http://localhost:8888/debug?asyncId=" + asyncId +
                "&dagElementId=" + dagElementId).get().build();
        ObjectMapper objectMapper = new ObjectMapper();

        Response response = null;
        try {
            response = httpClient.newCall(request).execute();
            if(response.code() == 200) {
                ApiResponse responseJson = objectMapper.readValue(response.body().string(), ApiResponse.class);

                DebugResult debugResult = objectMapper.convertValue(responseJson.getData(),
                        DebugResult.class);
                dagElementDebugResultMap.put(dagElementId, debugResult);
            }
        } catch (Exception e) {

        } finally {
            if(response != null) {
                response.close();
            }
        }
    }

    private void listen() throws IOException {
        Socket socket  = serverSocket.accept();
        InputStream is = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line = reader.readLine();
        if(line == null) {
            socket.close();
            return;
        }
        String[] tokens = line.split(" ");
        String apiEndpoint = tokens[1];

        if(apiEndpoint.contains(NEXT_LINE)) {
            nextLineEndpoint(socket, apiEndpoint);
        }
        if(apiEndpoint.contains(ADD_DEBUG_POINT)) {
            addDebugPoint(socket, apiEndpoint);
        }
        if(apiEndpoint.contains(REMOVE_DEBUG_POINT)) {
            removeDebugPoint(socket, apiEndpoint);
        }
        if(apiEndpoint.contains(GET)) {
            getDagElementDebugData(socket, apiEndpoint);
        }
    }

    private void getDagElementDebugData(Socket socket, String apiEndpoint) throws IOException {
        Map<String, String> queryParams = getQueryParams(apiEndpoint);
        String dagElementId = queryParams.get("dagElementId");
        if(!dagElementDebugResultMap.containsKey(dagElementId)) {
            getDebugData(asyncId, dagElementId);
        }
        respondWebPage(socket, apiEndpoint, dagElementId);
    }

    private void respondWebPage(Socket socket, String apiEndpoint, String dagElementId) throws IOException {
        DebugResult debugResult = dagElementDebugResultMap.get(dagElementId);
        try (OutputStream outputStream = socket.getOutputStream()) {
            String str = "HTTP/1.1 200 OK\\r\\n" + "Content-Type: text/html\\r\\n\\r\n";
            str += renderWebPage(dagElementId);
            outputStream.write(str.getBytes());
        } finally {
            socket.close();
        }
    }

    private String renderWebPage(String dagElementId) {
        DebugResult result = dagElementDebugResultMap.get(dagElementId);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<html>");
        Table table = new Table();
        String commonCode = result.getCommonCode();
        String[] commonCodeLines = commonCode.split("\n");
        for(String codeLine : commonCodeLines) {
            List<Cell> cellList = new ArrayList<>();
            cellList.add(new AddDebug(null, "ADD"));
            cellList.add(new AddDebug(null, "REMOVE"));
            cellList.add(new Cell(codeLine));
            table.addRow(cellList);
        }
        stringBuilder.append(table.get());
        stringBuilder.append("</hml>");

        return stringBuilder.toString();
    }

    private void removeDebugPoint(Socket socket, String apiEndpoint) throws IOException {
        Map<String, String> queryParams = getQueryParams(apiEndpoint);
        String dagElementId = queryParams.get("dagElementId");
        String line = queryParams.get("line");
        debugLine.get(dagElementId).remove(Integer.parseInt(line));
        debugLine.remove(line);
        socket.close();
    }

    private Map<String, String> getQueryParams(String apiEndpoint) {
        String query = apiEndpoint.split("/?")[1];
        Map<String, String> map = new HashMap<>();
        String[] queryParts = query.split("&");
        for(String queryPart : queryParts) {
            String[] parts = queryPart.split("=");
            map.put(parts[0], parts[1]);
        }
        return map;
    }

    private void addDebugPoint(Socket socket, String apiEndpoint) throws IOException {
        Map<String, String> queryParams = getQueryParams(apiEndpoint);
        String dagElementId = queryParams.get("dagElementId");
        String line = queryParams.get("line");
        Set<Integer> lines = debugLine.get(dagElementId);
        if(lines == null) {
            lines = new HashSet<>();
            debugLine.put(dagElementId, lines);
        }
        socket.close();
        lines.add(Integer.parseInt(line));
    }

    private void nextLineEndpoint(Socket socket, String apiEndpoint) throws IOException {
        Map<String, String> queryParams = getQueryParams(apiEndpoint);
        String dagElementId = queryParams.get("dagElementId");
        DebugResult debugResult = dagElementDebugResultMap.get(dagElementId);
        Set<Integer> debugPoints = debugLine.get(dagElementId);
        if(debugPoints == null) {
            socket.close();
        }
        while(debugResult.getDebugPointer() < debugResult.getUserReadableDebugPoint().size()) {
            DebugResult.UserReadableDebugPoint userReadableDebugPoint = debugResult.getUserReadableDebugPoint().get(debugResult.getDebugPointer());
            if(debugPoints.contains(userReadableDebugPoint.getCodePtr())) {
                passDebugValuesToClient(socket, objectMapper.writeValueAsString(userReadableDebugPoint));
                return;
            }
            debugResult.setDebugPointer(debugResult.getDebugPointer() + 1);
        }
        passDebugValuesToClient(socket, objectMapper.writeValueAsString(debugResult.getNextDagElementIds()));
    }

    private void passDebugValuesToClient(Socket socket, String jsonResponse) throws IOException {
        OutputStream outputStream = socket.getOutputStream();

        String responseHeaders = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + jsonResponse.length() + "\r\n" +
                "\r\n";
        String responseBody = jsonResponse;

        String response = responseHeaders + responseBody;
        outputStream.write(response.getBytes());

        outputStream.close();
        socket.close();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }
}

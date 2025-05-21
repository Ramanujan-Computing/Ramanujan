package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import in.ramanujan.rule.engine.NativeDebugger;
import in.ramanujan.translation.codeConverter.DagElement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;

public class DebugFetcher extends ExecuteInline {

    public DebugFetcher() {
        super();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<DebugInfoGatherElement> debugInfoGatherElementList = new ArrayList<>();

    class DebugInfoGatherElement {
        String dagElementId;
        List<NativeDebugger> debugInfo;
        int debugIndex = 0;
    }

    @Override
    protected void preProcess(DagElement dagElement) throws IOException {
        if (dagElement.getFirstCommandId().isEmpty()) {
            DebugInfoGatherElement debugInfoGatherElement = new DebugInfoGatherElement();
            debugInfoGatherElement.dagElementId = dagElement.getId();
            debugInfoGatherElement.debugInfo = new ArrayList<>();
            debugInfoGatherElementList.add(debugInfoGatherElement);
        }
    }

    @Override
    protected void postProcess(DagElement dagElement, in.ramanujan.rule.engine.NativeProcessor nativeProcessor) throws IOException {
        if (dagElement.getFirstCommandId().isEmpty()) return;
        DebugInfoGatherElement debugInfoGatherElement = new DebugInfoGatherElement();
        debugInfoGatherElement.dagElementId = dagElement.getId();
        debugInfoGatherElement.debugInfo = nativeProcessor != null ? nativeProcessor.debugPoints : new ArrayList<>();
        debugInfoGatherElementList.add(debugInfoGatherElement);
    }

    private void htmlFiles(List<DebugInfoGatherElement> debugInfoGatherElementList, String dirPath,
                           Map<String, String> dagElementAndCodeMap, String commonFunctionCode) throws IOException {
        for (DebugInfoGatherElement debugInfoGatherElement : debugInfoGatherElementList) {
            File htmlFile = new File(dirPath + "/" + debugInfoGatherElement.dagElementId + ".html");

            String code = "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <title>Code Line Server Call</title>\n" +
                    "    <style>\n" +
                    "        body {\n" +
                    "            display: flex;\n" +
                    "        }\n" +
                    "        .left {\n" +
                    "            width: 50%;\n" +
                    "        }\n" +
                    "        .right {\n" +
                    "            width: 50%;\n" +
                    "            padding-left: 20px;\n" +
                    "            border-left: 1px solid #ccc;\n" +
                    "        }\n" +
                    "        .code-line {\n" +
                    "            display: flex;\n" +
                    "            align-items: center;\n" +
                    "        }\n" +
                    "        button {\n" +
                    "            margin-left: 10px;\n" +
                    "        }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div class=\"left\">\n";
            String[] functionCodeLines = commonFunctionCode.split("\n");
            for (int i = 0; i < functionCodeLines.length; i++) {
                code += "        <div class=\"code-line\">\n" +
                        "            <code>" + functionCodeLines[i].replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;") + "</code>" +
                        "            <button onclick=\"makeServerCall(" + (i) + ")\">Run</button>\n" +
                        "        </div>\n";
            }
            String dagElementCode = dagElementAndCodeMap.get(debugInfoGatherElement.dagElementId);
            String[] dagElementCodeLines = dagElementCode.split("\n");
            for (int i = 0; i < dagElementCodeLines.length; i++) {
                code += "        <div class=\"code-line\">\n" +
                        "            <code>" + dagElementCodeLines[i].replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;") + "</code>" +
                        "            <button onclick=\"makeServerCall(" + (i + functionCodeLines.length) + ")\">Run</button>\n" +
                        "        </div>\n";
            }

            code += "    </div>\n" +
                    "    <div class=\"right\" id=\"response\">\n" +
                    "        <!-- Server response will be displayed here -->\n" +
                    "    </div>\n" +
                    "\n" +
                    "    <script>\n" +
                    "        function makeServerCall(lineNumber) {\n" +
                    "            fetch(`http://localhost:8000/execute?line=${lineNumber}&dagElementId=" + debugInfoGatherElement.dagElementId + "`)\n" +
                    "                .then(response => response.text())\n" +
                    "                .then(data => {\n" +
                    "                    document.getElementById('response').innerText = data;\n" +
                    "                })\n" +
                    "                .catch(error => {\n" +
                    "                    document.getElementById('response').innerText = 'Error: ' + error;\n" +
                    "                });\n" +
                    "        }\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>\n";

            htmlFile.delete();
            try (OutputStream os = new FileOutputStream(htmlFile)) {
                os.write(code.getBytes());
            }
        }

        Map<String, DebugInfoGatherElement> debugInfoGatherElementMap = new HashMap<>();
        for (DebugInfoGatherElement debugInfoGatherElement : debugInfoGatherElementList) {
            debugInfoGatherElementMap.put(debugInfoGatherElement.dagElementId, debugInfoGatherElement);
        }
        startDebuggingServer(dirPath, debugInfoGatherElementMap);
    }

    private void startDebuggingServer(String dirPath, Map<String, DebugInfoGatherElement> dagElementDebugInfoMap) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", (HttpExchange exchange) -> {
            String query = exchange.getRequestURI().getQuery();
            String[] params = query.split("&");
            Integer line = null;
            String dagElementId = null;
            for (String param : params) {
                String[] keyValue = param.split("=");
                if ("line".equalsIgnoreCase(keyValue[0])) {
                    line = Integer.parseInt(keyValue[1]);
                } else if ("dagElementId".equalsIgnoreCase(keyValue[0])) {
                    dagElementId = keyValue[1];
                }
            }

            DebugInfoGatherElement debugInfoGatherElement = dagElementDebugInfoMap.get(dagElementId);
            for (int lineNum = debugInfoGatherElement.debugIndex; lineNum < debugInfoGatherElement.debugInfo.size(); lineNum++) {
                NativeDebugger nativeDebugger = debugInfoGatherElement.debugInfo.get(lineNum);
                if (nativeDebugger.line == line) {
                    debugInfoGatherElement.debugIndex = lineNum + 1;
                    String response = objectMapper.writeValueAsString(nativeDebugger);

                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                    return;
                }
            }

            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
        });
        server.start();
    }

    private void printGraphDiagram(DagElement dagElement, String dirPath) throws IOException {
        List<List<DagElement>> layers = new ArrayList<>();
        Set<DagElement> visited = new HashSet<>();
        Queue<DagElement> queue = new LinkedList<>();
        queue.add(dagElement);
        while (!queue.isEmpty()) {
            List<DagElement> layer = new ArrayList<>();
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                DagElement element = queue.poll();
                if (visited.contains(element)) {
                    continue;
                }
                layer.add(element);
                visited.add(element);
                queue.addAll(element.getNextElements());
            }
            layers.add(layer);
        }

        String htmlCode = "<html lang=\"en\"> <head> <meta charset=\"UTF-8\"> <title>Graph Tree Visualization</title> " +
                "<style> svg { border: 1px solid #000; width: 100%; height: 100vh; } circle { fill: #69b3a2; } " +
                "text { font-family: Arial, sans-serif; font-size: 12px; fill: #000; } </style> <script src=\"dagElementGraph.js\"></script> </head> " +
                "<body> <svg id=\"graph\"></svg>  </body>";

        String scriptCode =
                "document.addEventListener('DOMContentLoaded', () => {" +
                        "const svg = document.getElementById('graph');\n" +
                        "const renderGraph = (data) => {\n" +
                        "    data.forEach(node => {\n" +
                        "        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n" +
                        "        group.setAttribute('id', `node-${node.id}`);\n" +
                        "        const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');\n" +
                        "        circle.setAttribute('cx', node.x);\n" +
                        "        circle.setAttribute('cy', node.y);\n" +
                        "        circle.setAttribute('r', 20);\n" +
                        "        circle.setAttribute('id', `node-${node.id}`);\n" +
                        "        const link = document.createElementNS('http://www.w3.org/2000/svg', 'a');\n" +
                        "        link.setAttribute('href', `${node.id}.html`);\n" +
                        "        link.setAttribute('x', node.x);\n" +
                        "        link.setAttribute('y', node.y + 5);\n" +
                        "        link.setAttribute('text-anchor', 'middle');\n" +
                        "        link.textContent = node.id;\n" +
                        "        link.appendChild(circle);\n" +
                        "        group.appendChild(link);svg.appendChild(group);\n" +
                        "        node.children.forEach(childId => {\n" +
                        "            const childNode = data.find(d => d.id === childId);\n" +
                        "            const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');\n" +
                        "            line.setAttribute('x1', node.x);\n" +
                        "            line.setAttribute('y1', node.y);\n" +
                        "            line.setAttribute('x2', childNode.x);\n" +
                        "            line.setAttribute('y2', childNode.y);\n" +
                        "            line.setAttribute('stroke', 'black');\n" +
                        "            svg.appendChild(line);\n" +
                        "        });\n" +
                        "    });\n" +
                        "};\n" +
                        "\n";

        StringBuilder data = new StringBuilder("const data = [");
        int layerHeight = 100;
        int nodeSpacing = 100;
        for (int i = 0; i < layers.size(); i++) {
            List<DagElement> layer = layers.get(i);
            int layerWidth = layer.size() * nodeSpacing;
            for (int j = 0; j < layer.size(); j++) {
                DagElement element = layer.get(j);
                int x = (j * nodeSpacing) + (nodeSpacing / 2);
                int y = i * layerHeight;
                data.append("{id: \"").append(element.getId()).append("\", x: ").append(x).append(", y: ").append(y).append(", children: [");
                for (DagElement child : element.getNextElements()) {
                    data.append("\"").append(child.getId()).append("\",");
                }
                data.append("]},");
            }
        }

        scriptCode += data.toString() + "];\n" +
                "renderGraph(data);})\n";

        File htmlFile = new File(dirPath + "/dagElementGraph.html");
        File scriptFile = new File(dirPath + "/dagElementGraph.js");
        htmlFile.createNewFile();
        scriptFile.createNewFile();
        try (OutputStream osHtml = new FileOutputStream(htmlFile)) {
            osHtml.write(htmlCode.getBytes());
        }
        try (OutputStream osScript = new FileOutputStream(scriptFile)) {
            osScript.write(scriptCode.getBytes());
        }
    }
}

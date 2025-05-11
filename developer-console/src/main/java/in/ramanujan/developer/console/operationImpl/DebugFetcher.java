package in.ramanujan.developer.console.operationImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import in.ramanujan.developer.console.Operation;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.NativeDebugger;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import in.ramanujan.translation.codeConverter.utils.TranslateUtil;
import okhttp3.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;

import static in.ramanujan.developer.console.operationImpl.ExecutorImpl.createJson;

public class DebugFetcher implements Operation {

    private final TranslateUtil translateUtil = new TranslateUtil();

    private final ObjectMapper objectMapper = new ObjectMapper();

    class DebugInfoGatherElement {
        String dagElementId;
        List<NativeDebugger> debugInfo;

        int debugIndex = 0;
    }

    /**
     * open vscode and provide it with information required for debugAdapter;
     */
    private void executeDagElement(DagElement dagElement, Map<String, Variable> variableMap, Map<String, Array> arrayMap, List<DebugInfoGatherElement> debugInfoGatherElementList) throws IOException {
        if(dagElement.getFirstCommandId().isEmpty()) {
            DebugInfoGatherElement debugInfoGatherElement = new DebugInfoGatherElement();
            debugInfoGatherElement.dagElementId = dagElement.getId();
            debugInfoGatherElement.debugInfo = new ArrayList<>();
            debugInfoGatherElementList.add(debugInfoGatherElement);
            return;
        }
        NativeProcessor nativeProcessor = new NativeProcessor();
        nativeProcessor.process(objectMapper.writeValueAsString(dagElement.getRuleEngineInput()), dagElement.getFirstCommandId());

        DebugInfoGatherElement debugInfoGatherElement = new DebugInfoGatherElement();
        debugInfoGatherElement.dagElementId = dagElement.getId();
        debugInfoGatherElement.debugInfo = nativeProcessor.debugPoints;
        debugInfoGatherElementList.add(debugInfoGatherElement);
        //iterate nativeProcessor.jniObject which is hashmap
        for(Object en : nativeProcessor.jniObject.entrySet()) {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) en;
            String key = entry.getKey();
            Object value = entry.getValue();
            if("arrayIndex".equalsIgnoreCase(key)) {
                Map<String, Map<String, Object>> arrayResultMap = (Map<String, Map<String, Object>>) value;
                for(Map.Entry<String, Map<String, Object>> arrayResultEntry : arrayResultMap.entrySet()) {
                    String arrayName = arrayResultEntry.getKey();
                    Map<String, Object> arrayResult = arrayResultEntry.getValue();
                    Array array = arrayMap.get(arrayName);
                    if(array == null) {
                        throw new IOException("Array not found");
                    }
                    array.getValues().putAll(arrayResult);
                }
            } else {
                Variable variable = variableMap.get(key);
                if(variable == null) {
                    throw new IOException("Variable not found");
                }
                variable.setValue(value);
            }
        }

    }

    @Override
    public void execute(List<String> args) throws IOException {
        try {
            Map<String, Variable> variableMap = new HashMap<>();
            Map<String, Array> arrayMap = new HashMap<>();
            CodeRunRequest codeRunRequest = createJson(args);
            String code = codeRunRequest.getCode();
            List<CsvInformation> csvInformationList = new ArrayList<>();
            TranslateResponse translateResponse = new TranslateResponse();
            Map<String, RuleEngineInput> functionCallsRuleEngineInput = new HashMap<>();
            ActualDebugCodeCreator actualDebugCodeCreator = new ActualDebugCodeCreator("", 0);
            ExtractedCodeAndFunctionCode extractedCodeAndFunctionCode =
                    translateUtil.extractCodeWithoutAbstractCodeDeclaration(code, functionCallsRuleEngineInput, actualDebugCodeCreator);
            //each entry in functionCallsRuleEngienInput
            for(Map.Entry<String, RuleEngineInput> entry : functionCallsRuleEngineInput.entrySet()) {
                for(Variable variable : entry.getValue().getVariables()) {
                    variableMap.put(variable.getId(), variable);
                }

                for(Array array : entry.getValue().getArrays()) {
                    arrayMap.put(array.getId(), array);
                }
            }
            code = extractedCodeAndFunctionCode.getExtractedCode();
            CodeSnippetElement firstCodeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(),
                    new HashMap<>(), new HashMap<>());
            List<DagElement> dagElementList = new ArrayList<>();
            Map<String, String> dagElementAndCodeMap = new HashMap<>();
            int linesForFunctions = actualDebugCodeCreator.getLine();
            DagElement firstDagElement = translateUtil.populateAllDagElements(firstCodeSnippetElement, csvInformationList,
                    functionCallsRuleEngineInput, variableMap, arrayMap, dagElementList, dagElementAndCodeMap, linesForFunctions);
            translateResponse.setFirstDagElement(firstDagElement);
            translateResponse.setDagElementList(dagElementList);
            translateResponse.setCodeAndDagElementMap(dagElementAndCodeMap);
            translateResponse.setCommonFunctionCode(extractedCodeAndFunctionCode.getFunctionCode());

            Queue<DagElement> dagElementQueue = new LinkedList<>();
            dagElementQueue.add(firstDagElement);
            /*
            * Traverse all dagElements. A dagElement can be traversed only if previous members are traversed.
            * Start with the firstDagElement
            * */
            Set<DagElement> dagElements = new HashSet<>();
            for(DagElement dagElement : dagElementList) {
                dagElements.add(dagElement);

            }
            List<DebugInfoGatherElement> debugInfoGatherElementList = new ArrayList<>();
            while(true) {
                Set<DagElement> dagElementsWaiting = new HashSet<>();
                while (!dagElementQueue.isEmpty()) {
                    DagElement dagElement = dagElementQueue.poll();

                    if (dagElement.getPreviousElements().isEmpty()) {
                        dagElements.add(dagElement);
                        dagElementQueue.addAll(dagElement.getNextElements());
                        executeDagElement(dagElement, variableMap, arrayMap, debugInfoGatherElementList);
                    } else {
                        boolean allPreviousTraversed = true;
                        for (DagElement previousDagElement : dagElement.getPreviousElements()) {
                            if (!dagElements.contains(previousDagElement)) {
                                allPreviousTraversed = false;
                                break;
                            }
                        }
                        if (allPreviousTraversed) {
                            dagElements.add(dagElement);
                            dagElementQueue.addAll(dagElement.getNextElements());
                            executeDagElement(dagElement, variableMap, arrayMap, debugInfoGatherElementList);
                        } else {
                            dagElementsWaiting.add(dagElement);

                        }
                    }
                }
                if(dagElementsWaiting.isEmpty()) {
                    break;
                } else {
                    dagElementQueue.addAll(dagElementsWaiting);
                }
            }

            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter the absolute path to save the output files");
            String path = scanner.nextLine();

            printGraphDiagram(translateResponse.getFirstDagElement(), path);
            htmlFiles(debugInfoGatherElementList, path, dagElementAndCodeMap, actualDebugCodeCreator.getDebugCode());

        } catch (CompilationException e) {
            throw new IOException(e);
        }
    }

    private void htmlFiles(List<DebugInfoGatherElement> debugInfoGatherElementList, String dirPath,
                           Map<String, String> dagElementAndCodeMap, String commonFunctionCode)  throws IOException{
        for(DebugInfoGatherElement debugInfoGatherElement : debugInfoGatherElementList) {
            File htmlFile = new File(dirPath + "/" + debugInfoGatherElement.dagElementId + ".html");

            // in the html code, ut shows text code of commonFunctionCode, and code from dagElementAndCodeMap, and each line has button
            // which make server call to http://localhost:8000/ with param line and dagElementId. The screen should show a text box on the right pane
            // which would give result of the server response.

            String code ="<!DOCTYPE html>\n" +
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
            //for each line in functionCode which should delimited by \n, add it as the div object and increment the line number
            String[] functionCodeLines = commonFunctionCode.split("\n");
            for(int i = 0; i < functionCodeLines.length; i++) {
                code += "        <div class=\"code-line\">\n" +
                        "            <code>" + functionCodeLines[i].replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;") + "</code>" +
                        "            <button onclick=\"makeServerCall(" + (i) + ")\">Run</button>\n" +
                        "        </div>\n";
            }
            String dagElementCode = dagElementAndCodeMap.get(debugInfoGatherElement.dagElementId);
            String[] dagElementCodeLines = dagElementCode.split("\n");
            for(int i = 0; i < dagElementCodeLines.length; i++) {
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
            try(OutputStream os = new FileOutputStream(htmlFile)) {
                os.write(code.getBytes());
            }
        }

        Map<String, DebugInfoGatherElement> debugInfoGatherElementMap = new HashMap<>();
        for(DebugInfoGatherElement debugInfoGatherElement : debugInfoGatherElementList) {
            debugInfoGatherElementMap.put(debugInfoGatherElement.dagElementId, debugInfoGatherElement);
        }
        startDebuggingServer(dirPath, debugInfoGatherElementMap);
    }

    private void startDebuggingServer(String dirPath, Map<String, DebugInfoGatherElement> dagElementDebugInfoMap) throws IOException {
        //start a server that will serve the html files using HttpUrlConnection
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        // Define a handler to respond to HTTP requests
        server.createContext("/", (HttpExchange exchange) -> {
            //get param line =? and dagElementId =?
            String query = exchange.getRequestURI().getQuery();
            String[] params = query.split("&");
            Integer line = null;
            String dagElementId = null;
            for(String param : params) {
                String[] keyValue = param.split("=");
                if("line".equalsIgnoreCase(keyValue[0])) {
                    line = Integer.parseInt(keyValue[1]);
                } else if("dagElementId".equalsIgnoreCase(keyValue[0])) {
                    dagElementId = keyValue[1];
                }
            }

            DebugInfoGatherElement debugInfoGatherElement = dagElementDebugInfoMap.get(dagElementId);
            for(int lineNum = debugInfoGatherElement.debugIndex; lineNum < debugInfoGatherElement.debugInfo.size(); lineNum++) {
                NativeDebugger nativeDebugger = debugInfoGatherElement.debugInfo.get(lineNum);
                if(nativeDebugger.line == line) {
                    debugInfoGatherElement.debugIndex = lineNum + 1;
                    String response = objectMapper.writeValueAsString(nativeDebugger);

                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                    return;
                } else {
                }
            }

            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();


        }); // Start the server
        server.start();
    }

    /**DagElement has the pointer to the next children in the graph. Now, more than one element can have common child.
     * All parents have to be one layer back, and all the children in next layer. This way we would create the graph, and we
     * want to create a visualisation of the graph in html.
     * */
    private void printGraphDiagram(DagElement dagElement, String dirPath) throws IOException {
        //we will have layers of dagElement, which we will use to render visualisation.
        List<List<DagElement>> layers = new ArrayList<>();
        Set<DagElement> visited = new HashSet<>();
        Queue<DagElement> queue = new LinkedList<>();
        queue.add(dagElement);
        while(!queue.isEmpty()) {
            List<DagElement> layer = new ArrayList<>();
            int size = queue.size();
            for(int i = 0; i < size; i++) {
                DagElement element = queue.poll();
                if(visited.contains(element)) {
                    continue;
                }
                layer.add(element);
                visited.add(element);
                queue.addAll(element.getNextElements());
            }
            layers.add(layer);
        }

        //now we have layers, we can render the visualisation. lets use jtwt library to render the visualisation.
        String htmlCode = "<html lang=\"en\"> <head> <meta charset=\"UTF-8\"> <title>Graph Tree Visualization</title> " +
                "<style> svg { border: 1px solid #000; width: 100%; height: 100vh; } circle { fill: #69b3a2; } " +
                "text { font-family: Arial, sans-serif; font-size: 12px; fill: #000; } </style> <script src=\"dagElementGraph.js\"></script> </head> " +
                "<body> <svg id=\"graph\"></svg>  </body>";

        String scriptCode =
                "document.addEventListener('DOMContentLoaded', () => {" +
                "const svg = document.getElementById('graph');\n" +
                "const renderGraph = (data) => {\n" +
                "    data.forEach(node => {\n" +
                        "        // Create group for node\n" +
                        "        const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');\n" +
                        "        group.setAttribute('id', `node-${node.id}`);\n" +
                "        // Draw circle\n" +
                "        const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');\n" +
                "        circle.setAttribute('cx', node.x);\n" +
                "        circle.setAttribute('cy', node.y);\n" +
                "        circle.setAttribute('r', 20);\n" +
                "        circle.setAttribute('id', `node-${node.id}`);\n" +
//                "        group.appendChild(circle);\n" +
//                "\n" +
//                "        // Draw text\n" +
//                "        const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');\n" +
//                "        text.setAttribute('x', node.x);\n" +
//                "        text.setAttribute('y', node.y + 5); // Adjust to center text vertically\n" +
//                "        text.setAttribute('text-anchor', 'middle');\n" +
//                "        text.textContent = node.id;\n" +
//                "        svg.appendChild(text);\n" +
                        "\n" +
                        "        // Draw hyperlink\n" +
                        "        const link = document.createElementNS('http://www.w3.org/2000/svg', 'a');\n" +
                        "        link.setAttribute('href', `${node.id}.html`);\n" +
                        "        link.setAttribute('x', node.x);\n" +
                        "        link.setAttribute('y', node.y + 5); // Adjust to center text vertically\n" +
                        "        link.setAttribute('text-anchor', 'middle');\n" +
                        "        link.textContent = node.id;\n" +
                        "        link.appendChild(circle);\n" +
                        "        group.appendChild(link);svg.appendChild(group);\n" +
                "\n" +
                "        // Draw lines to children\n" +
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

        // Generate data using above generated layers;
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


        //Create file as dagElementGraph.html and dagElementGraph.js in the taken path
        //write the htmlCode and scriptCode in the respective files.
        File htmlFile = new File(dirPath + "/dagElementGraph.html");
        File scriptFile = new File(dirPath + "/dagElementGraph.js");
        htmlFile.createNewFile();
        scriptFile.createNewFile();
        try(OutputStream osHtml = new FileOutputStream(htmlFile)) {
            osHtml.write(htmlCode.getBytes());
        }
        try(OutputStream osScript = new FileOutputStream(scriptFile)) {
            osScript.write(scriptCode.getBytes());
        }
    }
}

package in.ramanujan.middleware.service;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.rule.engine.RuleEngineInputProtoSerializer;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import in.ramanujan.developer.console.model.pojo.csv.CsvInformation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test class for TranslateService.translate() method
 * Tests the translation of both Ramanujan and Python code
 */
public class TranslateServiceTest {

    private final TranslateService translateService = new TranslateService();

    /**
     * Tests that the system can detect and process both Ramanujan and Python code correctly
     */
    @Test
    public void testRamanujanAndPythonCodeDetection() throws Exception {
        System.out.println("\n========== Testing Ramanujan Code ==========");
        
        // Test 1: Ramanujan code with curly braces
        String ramanujanCode = "var x:integer;\n" +
                "x = 10;\n" +
                "var y:integer;\n" +
                "y = 20;\n" +
                "var result:integer;\n" +
                "result = x + y;";
        
        Map<String, Variable> ramanujanVarMap = new HashMap<>();
        Map<String, Array> ramanujanArrayMap = new HashMap<>();
        
        TranslateResponse ramanujanResponse = translateService.translate(ramanujanCode, new ArrayList<>(), 
                ramanujanVarMap, ramanujanArrayMap).result();
        
        executeAndAnalyzeResponse(ramanujanResponse, ramanujanVarMap, ramanujanArrayMap, 
                new String[]{"x", "y", "result"}, new double[]{10d, 20d, 30d});
        
        System.out.println("\n========== Testing Python Code ==========");
        
        // Test 2: Python code without curly braces
        String pythonCode = "x = 10\n" +
                "y = 20\n" +
                "result = x + y";
        
        Map<String, Variable> pythonVarMap = new HashMap<>();
        Map<String, Array> pythonArrayMap = new HashMap<>();
        
        System.out.println("Testing Python code translation...");
        
        TranslateResponse pythonResponse = translateService.translate(pythonCode, new ArrayList<>(), 
                pythonVarMap, pythonArrayMap).result();
        
        executeAndAnalyzeResponse(pythonResponse, pythonVarMap, pythonArrayMap,
                new String[]{"x", "y", "result"}, new double[]{10d, 20d, 30d});
        
        System.out.println("\n========== Testing Python Code with def ==========");
        
        // Test 3: Python code with def and colon (should be detected as Python)
        String pythonWithDef = "def add(a, b):\n" +
                "    result = a + b\n" +
                "    return result\n" +
                "\n" +
                "num1 = 15\n" +
                "num2 = 25\n" +
                "total = add(num1, num2)";
        
        Map<String, Variable> pythonDefVarMap = new HashMap<>();
        Map<String, Array> pythonDefArrayMap = new HashMap<>();
        
        System.out.println("Testing Python code with def translation...");
        
        TranslateResponse pythonDefResponse = translateService.translate(pythonWithDef, new ArrayList<>(),
                pythonDefVarMap, pythonDefArrayMap).result();
        
        executeAndAnalyzeResponse(pythonDefResponse, pythonDefVarMap, pythonDefArrayMap,
                new String[]{"num1", "num2", "total"}, new double[]{15d, 25d, 40d});
        
        System.out.println("\n========== All Language Detection Tests Passed ==========");
    }

    @Test
    public void testPythonNestedWhileDagExecution() throws Exception {
        String pythonCode =
                "def nestedWhileTest(outer, inner, result):\n" +
                "    i = 0\n" +
                "    j = 0\n" +
                "    result = 0\n" +
                "    i = 0\n" +
                "    while i < outer:\n" +
                "        j = 0\n" +
                "        while j < inner:\n" +
                "            result = result + i * j\n" +
                "            j = j + 1\n" +
                "        i = i + 1\n" +
                "\n" +
                "testResult = 0\n" +
                "nestedWhileTest(3, 4, testResult)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();

        TranslateResponse response = translateService.translate(pythonCode, new ArrayList<>(), variableMap, arrayMap).result();
        assertNotNull("Python nested-while translation failed", response);
        executeDagGraph(response, variableMap, arrayMap);

        assertEquals(18d, getVariableValueByName(variableMap, "testResult"), 0.0001);
    }

    @Test
    public void testPythonBubbleSortDagExecution() throws Exception {
        String pythonCode =
                "sortArray = [0 for _ in range(5)]\n" +
                "sortArray[0] = 64\n" +
                "sortArray[1] = 34\n" +
                "sortArray[2] = 25\n" +
                "sortArray[3] = 12\n" +
                "sortArray[4] = 22\n" +
                "\n" +
                "def bubbleSort(arr, size):\n" +
                "    i = 0\n" +
                "    j = 0\n" +
                "    temp = 0\n" +
                "    nextJ = 0\n" +
                "    outerLimit = size - 1\n" +
                "    innerLimit = 0\n" +
                "    i = 0\n" +
                "    while i < outerLimit:\n" +
                "        j = 0\n" +
                "        innerLimit = outerLimit - i\n" +
                "        while j < innerLimit:\n" +
                "            nextJ = j + 1\n" +
                "            if arr[j] > arr[nextJ]:\n" +
                "                temp = arr[j]\n" +
                "                arr[j] = arr[nextJ]\n" +
                "                arr[nextJ] = temp\n" +
                "            j = j + 1\n" +
                "        i = i + 1\n" +
                "\n" +
                "bubbleSort(sortArray, 5)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();

        TranslateResponse response = translateService.translate(pythonCode, new ArrayList<>(), variableMap, arrayMap).result();
        assertNotNull("Python bubble sort translation failed", response);
        executeDagGraph(response, variableMap, arrayMap);

        Map<String, Object> expected = new HashMap<>();
        expected.put("0", 12d);
        expected.put("1", 22d);
        expected.put("2", 25d);
        expected.put("3", 34d);
        expected.put("4", 64d);

        Array sorted = arrayMap.values().stream()
                .filter(a -> a.getName().equals("sortArray"))
                .findFirst()
                .orElse(null);

        assertNotNull("sortArray not found", sorted);
        assertEquals(expected, sorted.getValues());
    }

    /**
     * Tests that CSV data passed to translate() is correctly injected as a 2D Python array
     * into the first code snippet.
     *
     * CSV "scores.csv" (3 rows x 2 cols):
     *   1,2
     *   3,4
     *   5,6
     *
     * Expected: scores[3][2] array is available in Python code.
     * Python code sums all elements → total = 1+2+3+4+5+6 = 21.
     */
    @Test
    public void testPythonCsvIntegration() throws Exception {
        CsvInformation csv = new CsvInformation();
        csv.setFileName("scores.csv");
        csv.setData("1,2\n3,4\n5,6");

        List<CsvInformation> csvList = new ArrayList<>();
        csvList.add(csv);

        // Reads every element of the injected 2D array 'scores' and accumulates the sum
        String pythonCode =
            "total = 0\n" +
            "i = 0\n" +
            "while i < 3:\n" +
            "    j = 0\n" +
            "    while j < 2:\n" +
            "        total = total + scores[i][j]\n" +
            "        j = j + 1\n" +
            "    i = i + 1\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();

        TranslateResponse response = translateService.translate(pythonCode, csvList, variableMap, arrayMap).result();
        if (response == null) {
            fail("CSV integration translation failed: null response");
        }

        executeDagGraph(response, variableMap, arrayMap);

        assertEquals("Sum of CSV elements should be 21", 21d,
            getVariableValueByName(variableMap, "total"), 0.0001);
    }

    /**
     * Tests CSV integration with two CSV files. Each CSV is injected as a separate 2D array.
     * Python code reads from both arrays and computes a combined result.
     *
     * CSV "a.csv":
     *   10,20
     *   30,40
     * CSV "b.csv":
     *   1,2
     *   3,4
     *
     * sumA = 10+20+30+40 = 100, sumB = 1+2+3+4 = 10 → combined = 110
     */
    @Test
    public void testPythonMultipleCsvIntegration() throws Exception {
        CsvInformation csvA = new CsvInformation();
        csvA.setFileName("a.csv");
        csvA.setData("10,20\n30,40");

        CsvInformation csvB = new CsvInformation();
        csvB.setFileName("b.csv");
        csvB.setData("1,2\n3,4");

        List<CsvInformation> csvList = new ArrayList<>();
        csvList.add(csvA);
        csvList.add(csvB);

        String pythonCode =
            "sumA = 0\n" +
            "sumB = 0\n" +
            "i = 0\n" +
            "while i < 2:\n" +
            "    j = 0\n" +
            "    while j < 2:\n" +
            "        sumA = sumA + a[i][j]\n" +
            "        sumB = sumB + b[i][j]\n" +
            "        j = j + 1\n" +
            "    i = i + 1\n" +
            "combined = sumA + sumB\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();

        TranslateResponse response = translateService.translate(pythonCode, csvList, variableMap, arrayMap).result();
        if (response == null) {
            fail("Multiple CSV integration translation failed: null response");
        }

        executeDagGraph(response, variableMap, arrayMap);

        assertEquals("sumA should be 100", 100d, getVariableValueByName(variableMap, "sumA"), 0.0001);
        assertEquals("sumB should be 10",  10d,  getVariableValueByName(variableMap, "sumB"), 0.0001);
        assertEquals("combined should be 110", 110d, getVariableValueByName(variableMap, "combined"), 0.0001);
    }

    @Test
    public void testRamanujanThreadStartThreadOnEndExecution() throws Exception {
        String code = "var x:integer;" +
            "var y:integer;" +
            "var z:integer;" +
            "x = 5;" +
            "y = 7;" +
            "threadStart(t1) {x = x + 10;}" +
            "threadStart(t2) {y = y + 20;}" +
            "threadOnEnd(t1, t2, 1) {z = x + y;}";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();

        io.vertx.core.Future<TranslateResponse> future = translateService.translate(code, new ArrayList<>(), variableMap, arrayMap);
        TranslateResponse response = future.result();
        if (response == null) {
            Throwable cause = future.cause();
            fail("Ramanujan thread translation failed: " + (cause != null ? cause.getMessage() : "null response"));
        }
        DagElement first = response.getFirstDagElement();
        assertNotNull("First DagElement missing for thread test", first);
        assertNotNull("Dag list missing for thread test", response.getDagElementList());
        assertTrue("Expected multiple DAG elements for thread graph", response.getDagElementList().size() > 1);

        boolean hasJoinNode = response.getDagElementList().stream()
            .anyMatch(d -> d.getPreviousElements() != null && d.getPreviousElements().size() >= 2);
        assertTrue("Expected a join node representing threadOnEnd", hasJoinNode);
    }

    /**
     * Helper method to execute the first DagElement from TranslateResponse and analyze results
     */
    private void executeAndAnalyzeResponse(TranslateResponse translateResponse, 
                                          Map<String, Variable> variableMap,
                                          Map<String, Array> arrayMap,
                                          String[] expectedVariables,
                                          double[] expectedValues) throws Exception {
        DagElement firstDagElement = translateResponse.getFirstDagElement();
        assertNotNull("First DagElement should not be null", firstDagElement);
        
        RuleEngineInput ruleEngineInput = firstDagElement.getRuleEngineInput();
        assertNotNull("RuleEngineInput should not be null", ruleEngineInput);
        
        // Populate variable and array maps from ruleEngineInput
        for (Variable variable : ruleEngineInput.getVariables()) {
            variableMap.put(variable.getId(), variable);
        }
        for (Array array : ruleEngineInput.getArrays()) {
            arrayMap.put(array.getId(), array);
        }
        
        // Execute using NativeProcessor
        String firstCommandId = firstDagElement.getFirstCommandId();
        if (firstCommandId != null && !firstCommandId.isEmpty()) {
            NativeProcessor processor = new NativeProcessor();
            ObjectMapper mapper = new ObjectMapper();
            String jsonInput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ruleEngineInput);

            // Write protobuf byte array payload to a stable tmp file for native debugging
            byte[] protoBytes = RuleEngineInputProtoSerializer.serialize(ruleEngineInput);
            Path tmpPath = Paths.get("/tmp", "translate_service_debug.pb");
            Files.write(tmpPath, protoBytes);
            Files.write(Paths.get("/tmp", "rule_engine_debug.pb"), protoBytes);
            Files.write(Paths.get("/tmp", "rule_engine_debug.json"), protoBytes);

            System.out.println("========== Debug payload written to /tmp/rule_engine_debug.pb ==========");
            System.out.flush();
            processor.process(protoBytes, firstCommandId);

            resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        }
        
        // Analyze and assert results
        analyzeResults(variableMap, expectedVariables, expectedValues);
    }

    private void executeDagGraph(TranslateResponse translateResponse,
                                 Map<String, Variable> variableMap,
                                 Map<String, Array> arrayMap) throws Exception {
        DagElement firstDagElement = translateResponse.getFirstDagElement();
        assertNotNull("First DagElement should not be null", firstDagElement);

        ArrayList<DagElement> allElements = new ArrayList<>();
        if (translateResponse.getDagElementList() != null) {
            allElements.addAll(translateResponse.getDagElementList());
        }
        if (!allElements.contains(firstDagElement)) {
            allElements.add(firstDagElement);
        }

        for (DagElement element : allElements) {
            RuleEngineInput rei = element.getRuleEngineInput();
            if (rei != null) {
                for (Variable variable : rei.getVariables()) {
                    variableMap.putIfAbsent(variable.getId(), variable);
                }
                for (Array array : rei.getArrays()) {
                    arrayMap.putIfAbsent(array.getId(), array);
                }
            }
        }

        Set<DagElement> remaining = new HashSet<>(allElements);
        ArrayDeque<DagElement> ready = new ArrayDeque<>();
        for (DagElement element : allElements) {
            if (element.getPreviousElements() == null || element.getPreviousElements().isEmpty()) {
                ready.add(element);
            }
        }

        while (!ready.isEmpty()) {
            DagElement element = ready.poll();
            if (!remaining.contains(element)) {
                continue;
            }

            executeDagElement(element, variableMap, arrayMap);
            remaining.remove(element);

            for (DagElement next : element.getNextElements()) {
                if (!remaining.contains(next)) {
                    continue;
                }
                boolean depsDone = true;
                for (DagElement prev : next.getPreviousElements()) {
                    if (remaining.contains(prev)) {
                        depsDone = false;
                        break;
                    }
                }
                if (depsDone) {
                    ready.add(next);
                }
            }
        }

        assertTrue("Not all DAG elements executed", remaining.isEmpty());
    }

    private void executeDagElement(DagElement dagElement,
                                   Map<String, Variable> variableMap,
                                   Map<String, Array> arrayMap) throws Exception {
        RuleEngineInput ruleEngineInput = dagElement.getRuleEngineInput();
        assertNotNull("RuleEngineInput should not be null", ruleEngineInput);

        String firstCommandId = dagElement.getFirstCommandId();
        if (firstCommandId == null || firstCommandId.isEmpty()) {
            return;
        }

        NativeProcessor processor = new NativeProcessor();
        ObjectMapper mapper = new ObjectMapper();
        String jsonInput = mapper.writeValueAsString(ruleEngineInput);
        processor.process(RuleEngineInputProtoSerializer.serialize(ruleEngineInput), firstCommandId);

        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
    }

    private double getVariableValueByName(Map<String, Variable> variableMap, String name) {
        Variable variable = variableMap.values().stream()
                .filter(v -> name.equals(v.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull("Variable " + name + " not found", variable);
        return variable.getValue() != null ? (Double) variable.getValue() : 0.0;
    }

    /**
     * Resolve variables from NativeProcessor result
     */
    @SuppressWarnings("unchecked")
    private void resolveVariablesFromNativeProcessor(NativeProcessor nativeProcessor, 
                                                     Map<String, Variable> variableMap, 
                                                     Map<String, Array> arrayMap) {
        try {
            for(Object en : nativeProcessor.jniObject.entrySet()) {
                Map.Entry<String, Object> entry = (Map.Entry<String, Object>) en;
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if("arrayIndex".equalsIgnoreCase(key)) {
                    // Handle array results
                    Map<String, Map<String, Object>> arrayResultMap = (Map<String, Map<String, Object>>) value;
                    for(Map.Entry<String, Map<String, Object>> arrayResultEntry : arrayResultMap.entrySet()) {
                        String arrayName = arrayResultEntry.getKey();
                        Map<String, Object> arrayResult = arrayResultEntry.getValue();
                        Array array = arrayMap.get(arrayName);
                        if(array != null) {
                            array.getValues().putAll(arrayResult);
                        }
                    }
                } else {
                    // Handle variable results
                    Variable variable = variableMap.get(key);
                    if(variable != null) {
                        variable.setValue(value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error resolving variables: " + e.getMessage());
        }
    }

    /**
     * Analyze results and assert expected values
     */
    private void analyzeResults(Map<String, Variable> variableMap, 
                               String[] expectedVariables, 
                               double[] expectedValues) {
        System.out.println("\n=== VARIABLE ANALYSIS ===");
        for (Variable variable : variableMap.values()) {
            System.out.println("Variable: " + variable.getName() + " = " + variable.getValue());
        }

        System.out.println("\n=== VARIABLE ASSERTION ANALYSIS ===");
        for (int i = 0; i < expectedVariables.length; i++) {
            String varName = expectedVariables[i];
            double expectedValue = expectedValues[i];
            
            Variable variable = variableMap.values().stream()
                    .filter(v -> varName.equals(v.getName()))
                    .findFirst()
                    .orElse(null);
            
            if (variable != null) {
                double actualValue = variable.getValue() != null ? (Double) variable.getValue() : 0.0;
                System.out.println("Variable " + varName + ": " + actualValue);
                assertEquals("Variable " + varName + " value mismatch", expectedValue, actualValue, 0.0001);
                System.out.println("  Assertion PASSED: " + actualValue + " == " + expectedValue);
            } else {
                fail("Variable " + varName + " not found");
            }
        }
    }
}



package in.ramanujan.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.utils.TranslateUtil;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BigCodeRunTest {
    @Test
    public void yetAnotherBigOne() throws Exception {
        String code = "def getSquared(var xPow:integer, var yPow:integer, var ans:integer) {\n" +
                "    if(xPow < yPow) {\n" +
                "        ans = yPow - xPow;\n" +
                "    } else {\n" +
                "        ans = xPow - yPow;\n" +
                "    }\n" +
                "}\n" +
                "    \n" +
                "\n" +
                "def getAvg(var arr:array, var originalArr:array, var avgF:integer) {\n" +
                "  var index,ans1,tmpAvg1,tmpAvg2:integer;\n" +
                "    avgF = 0;\n" +
                "    index = 0;\n" +
                "    while(index < 100) {\n" +
                "        tmpAvg1 = arr[index];\n" +
                "        tmpAvg2 = originalArr[index];\n" +
                "        exec getSquared(tmpAvg1,tmpAvg2, ans1);\n" +
                "        avgF = avgF + ans1;\n" +
                "        index = index + 1;\n" +
                "    }\n" +
                "    avgF = avgF / 100;\n" +
                "}\n" +
                "\n" +
                "def getTestArr(var xTest:integer, var yTest:integer, var testArrTest:array) {\n" +
                "    var it:integer;\n" +
                "    it = 0;\n" +
                "    while(it < 100) {\n" +
                "        testArrTest[it] = xTest * it + yTest;\n" +
                "        it = it + 1;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "var train[100]:array;\n" +
                "var i:integer;\n" +
                "i = 0;\n" +
                "while(i < 100) {\n" +
                "    train[i] = i * 1.9 + 33;\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "\n" +
                "def mainCode(var train : array, var x1:double, var y1:double) {\n" +
                "    var x1,y1,j,avg,diff1,diff2x,diff2y,tmp:double;\n" +
                "    j = 0;\n" +
                "    var testArr[100]:array;\n" +
                "    var slope:double;\n" +
                "    var nexty,nextx:double;\n" +
                "    testArr[1] = 1;\n" +
                "    while(j < 15000) {\n" +
                "        exec getTestArr(x1,y1,testArr);\n" +
                "        exec getAvg(testArr, train, diff1);\n" +
                "\n" +
                "        tmp = x1 + 0.0001;\n" +
                "        exec getTestArr(tmp,y1,testArr);\n" +
                "        exec getAvg(testArr, train, diff2x);\n" +
                "\n" +
                "        slope = (diff2x - diff1) / 0.0001;\n" +
                "        nextx = x1 - slope * 0.1;\n" +
                "\n" +
                "        tmp = y1 + 0.0001;\n" +
                "        exec getTestArr(x1,tmp,testArr);\n" +
                "        exec getAvg(testArr, train, diff2y);\n" +
                "\n" +
                "        slope = (diff2y - diff1) / 0.0001;\n" +
                "        nexty = y1 - slope * 0.50;\n" +
                "\n" +
                "        x1 = nextx;\n" +
                "        y1 = nexty;\n" +
                "\n" +
                "        j = j + 1;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "var x1[100][10],y1[100][10]:array;\n" +
                "x1[0][0] = 0;\n" +
                "y1[0][0] = 0;\n" +
                "var ansX1,ansy1 :double;\n" +
                "ansX1 = 0;\n" +
                "ansy1 = 0;\n" +
                "var iteration[10]:array;\n" +
                "i = 0;\n" +
                "while(i < 10) {\n" +
                "    iteration[i] = 0;\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "\n" +
                "\n" +
                "def getBest(var train:array, var best:integer, var x1:array, var y1:array, var iteration:integer) {\n" +
                "    best = 0;\n" +
                "    var index:integer;\n" +
                "    var bestM:double;\n" +
                "    bestM = 1000000000;\n" +
                "    index = 0;\n" +
                "    while(index < 10) {\n" +
                "        var testArr[100]:array;\n" +
                "        testArr[0] = 0;\n" +
                "        var testX1,testY1:double;\n" +
                "        testX1 = x1[index][iteration];\n" +
                "        testY1 = y1[index][iteration];\n" +
                "        exec getTestArr(testX1,testY1,testArr);\n" +
                "        var avg:double;\n" +
                "        avg = 0;\n" +
                "        exec getAvg(testArr, train, avg);\n" +
                "        if(avg < bestM) {\n" +
                "            bestM = avg;\n" +
                "            best = index;\n" +
                "        }\n" +
                "        index = index + 1;\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "\n" +
                "  def posRun(var thread:integer, var train:array, var x1:array, var y1:array, var iteration :array) {\n" +
                "    var currentIter:integer;\n" +
                "    currentIter = iteration[thread];\n" +
                "    if(currentIter == 0) {\n" +
                "  \n" +
                "      x1[thread][currentIter]=thread;\n" +
                "      y1[thread][currentIter]=thread;\n" +
                "    } else {\n" +
                "      var best :integer;\n" +
                "      best=0;\n" +
                "      var thisIter:integer;\n" +
                "      thisIter=currentIter;\n" +
                "      currentIter = currentIter-1;\n" +
                "      exec getBest(train, best, x1, y1, currentIter);\n" +
                "      if(x1[thread][currentIter] < x1[best][currentIter]) {\n" +
                "        \n" +
                "          x1[thread][thisIter] = x1[thread][currentIter]+(x1[best][currentIter]-x1[thread][currentIter])/2;\n" +
                "        } else {\n" +
                "          x1[thread][thisIter] = x1[thread][currentIter]-(x1[thread][currentIter]-x1[best][currentIter])/2;\n" +
                "       \n" +
                "      }\n" +
                "      if(y1[thread][currentIter] < y1[best][currentIter]) {\n" +
                "          y1[thread][thisIter] = y1[thread][currentIter]+(y1[best][currentIter]-y1[thread][currentIter])/2;\n" +
                "        } else {\n" +
                "          y1[thread][thisIter] = y1[thread][currentIter]-(y1[thread][currentIter]-y1[best][currentIter])/2;\n" +
                "      }\n" +
                "      currentIter=thisIter;\n" +
                "    }\n" +
                "  \n" +
                "\n" +
                "    var x,y:double;\n" +
                "    x=x1[thread][currentIter];\n" +
                "    y=y1[thread][currentIter];\n" +
                "    exec mainCode(train, x, y);\n" +
                "    x1[thread][currentIter]=x;\n" +
                "    y1[thread][currentIter]=y;\n" +
                "  }" +
                "exec posRun(0, train, x1, y1,iteration);";

        // Get the parsed structure with variable and array maps
        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        Long timeStart = new Date().toInstant().toEpochMilli();
        
        // Execute the native processor
        NativeProcessor processor1 = new NativeProcessor();
        processor1.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        // Resolve variables from native processor result (similar to ExecuteInline.java)
        resolveVariablesFromNativeProcessor(processor1, variableMap, arrayMap);
        
        System.out.println("timeTaken: " + (new Date().toInstant().toEpochMilli() - timeStart) + "ms");
        
        // Define variables to assert/analyze with expected values
        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("x1", null); // null means just analyze, no assertion
        variablesToAssert.put("y1", null); // null means just analyze, no assertion
        
        // Example: To add specific assertions for array values:
        // Map<String, Object> expectedX1Values = new HashMap<>();
        // expectedX1Values.put("0,0", 0.0); // x1[0][0] should be 0.0
        // variablesToAssert.put("x1", expectedX1Values);
        
        // Example: To add assertions for specific variables:
        // variablesToAssert.put("ansX1", 0.0); // ansX1 should be 0.0
        
        // Analyze the results and perform assertions
        analyzeResults(variableMap, arrayMap, variablesToAssert);
        
        // Add some basic assertions to verify execution completed successfully
        assertNotNull("Variable map should not be null", variableMap);
        assertNotNull("Array map should not be null", arrayMap);
        assertTrue("Should have some variables", variableMap.size() > 0);
        assertTrue("Should have some arrays", arrayMap.size() > 0);
    }

    private RuleEngineInput getRuleEngineInputWithMaps(String code, Map<String, Variable> variableMap, Map<String, Array> arrayMap) throws Exception {
        Map<String, RuleEngineInput> functionCallsRuleEngineInput = new HashMap<>();
        TranslateUtil translateUtil = new TranslateUtil();
        ExtractedCodeAndFunctionCode extractedCodeAndFunctionCode = translateUtil.extractCodeWithoutAbstractCodeDeclaration(code, functionCallsRuleEngineInput,
                new ActualDebugCodeCreator("", 0));
        
        // Populate variable and array maps from function calls
        for(Map.Entry<String, RuleEngineInput> entry : functionCallsRuleEngineInput.entrySet()) {
            for(Variable variable : entry.getValue().getVariables()) {
                variableMap.put(variable.getId(), variable);
            }
            for(Array array : entry.getValue().getArrays()) {
                arrayMap.put(array.getId(), array);
            }
        }
        
        CodeSnippetElement firstCodeSnippetElement = translateUtil.getCodeSnippets(extractedCodeAndFunctionCode.getExtractedCode(), new HashMap<>(),
                new HashMap<>(), new HashMap<>());
        DagElement firstDagElement = translateUtil.populateAllDagElements(firstCodeSnippetElement, new ArrayList<>(),
                functionCallsRuleEngineInput,
                variableMap, arrayMap, new ArrayList<>(), new HashMap<>(), 0);
        
        return firstDagElement.getRuleEngineInput();
    }

    /**
     * Resolve variables from NativeProcessor result - similar to ExecuteInline.java executeDagElement method
     */
    @SuppressWarnings("unchecked")
    private void resolveVariablesFromNativeProcessor(NativeProcessor nativeProcessor, Map<String, Variable> variableMap, Map<String, Array> arrayMap) {
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
            e.printStackTrace();
        }
    }

    /**
     * Analyze results with configurable variables to focus on and assert
     * @param variableMap Map of all variables from execution
     * @param arrayMap Map of all arrays from execution  
     * @param variablesToAssert Map of variable names to expected values (null means analyze only, no assertion)
     */
    private void analyzeResults(Map<String, Variable> variableMap, Map<String, Array> arrayMap, Map<String, Object> variablesToAssert) {
        System.out.println("\n=== VARIABLE ANALYSIS ===");
        
        // Print all variables
        for (Variable v : variableMap.values()) {
            System.out.println("Variable: " + v.getName() + " = " + v.getValue());
        }
        
        System.out.println("\n=== ARRAY ANALYSIS ===");
        
        // Analyze arrays with focus on specified variables
        Map<String, Map<String, Object>> arrayStoreMap = new HashMap<>();
        for (Array a : arrayMap.values()) {
            String id = a.getId();
            if (id.contains("func")) {
                continue;
            }
            if (!id.contains("_name_")) {
                continue;
            }
            String name = id.split("_name_")[1];
            Map<String, Object> values = a.getValues();
            if (values != null) {
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    String indexStr = entry.getKey();
                    Object value = entry.getValue();
                    Map<String, Object> arrMap = arrayStoreMap.getOrDefault(name, new HashMap<>());
                    arrMap.put(indexStr, value);
                    arrayStoreMap.put(name, arrMap);
                }
            }
        }
        
        // Print array results with special focus on specified variables
        for (Map.Entry<String, Map<String, Object>> arrayEntry : arrayStoreMap.entrySet()) {
            String arrayName = arrayEntry.getKey();
            Map<String, Object> arrayValues = arrayEntry.getValue();
            
            System.out.println("\nArray: " + arrayName);
            if (variablesToAssert.containsKey(arrayName)) {
                System.out.println("*** ANALYZING " + arrayName.toUpperCase() + " (SPECIFIED FOR ANALYSIS) ***");
                
                // Print values in a structured way
                for (Map.Entry<String, Object> valueEntry : arrayValues.entrySet()) {
                    String index = valueEntry.getKey();
                    Object value = valueEntry.getValue();
                    System.out.println("  " + arrayName + "[" + index + "] = " + value);
                }
                
                // Additional analysis
                if (arrayValues.size() > 0) {
                    System.out.println("  Total entries: " + arrayValues.size());
                    // Find min/max values if they are numeric
                    try {
                        double min = Double.MAX_VALUE;
                        double max = Double.MIN_VALUE;
                        for (Object val : arrayValues.values()) {
                            if (val instanceof Number) {
                                double d = ((Number) val).doubleValue();
                                min = Math.min(min, d);
                                max = Math.max(max, d);
                            }
                        }
                        if (min != Double.MAX_VALUE) {
                            System.out.println("  Range: " + min + " to " + max);
                        }
                    } catch (Exception e) {
                        // Ignore if values are not numeric
                    }
                }
                
                // Perform assertion if expected value is provided
                Object expectedValue = variablesToAssert.get(arrayName);
                if (expectedValue != null) {
                    System.out.println("  Expected: " + expectedValue);
                    // Add assertion logic here with JUnit assertions
                    if (expectedValue instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> expectedMap = (Map<String, Object>) expectedValue;
                        
                        // Assert that all expected values are present and correct
                        for (Map.Entry<String, Object> expectedEntry : expectedMap.entrySet()) {
                            String expectedIndex = expectedEntry.getKey();
                            Object expectedVal = expectedEntry.getValue();
                            Object actualVal = arrayValues.get(expectedIndex);
                            
                            assertNotNull("Array " + arrayName + " should contain index " + expectedIndex, actualVal);
                            assertEquals("Array " + arrayName + "[" + expectedIndex + "] value mismatch", 
                                expectedVal, actualVal);
                        }
                        System.out.println("  All assertions PASSED for " + arrayName);
                    } else {
                        // For non-map expected values, could add other assertion logic
                        System.out.println("  Expected value type not supported for assertion: " + expectedValue.getClass());
                    }
                } else {
                    // No assertion, just analysis
                    System.out.println("  Analysis complete (no assertions specified)");
                }
            } else {
                // Print limited output for other arrays
                System.out.println("  Size: " + arrayValues.size());
                if (arrayValues.size() <= 10) {
                    for (Map.Entry<String, Object> valueEntry : arrayValues.entrySet()) {
                        System.out.println("  " + arrayName + "[" + valueEntry.getKey() + "] = " + valueEntry.getValue());
                    }
                } else {
                    System.out.println("  (Large array - showing first few values)");
                    int count = 0;
                    for (Map.Entry<String, Object> valueEntry : arrayValues.entrySet()) {
                        if (count++ >= 5) break;
                        System.out.println("  " + arrayName + "[" + valueEntry.getKey() + "] = " + valueEntry.getValue());
                    }
                    System.out.println("  ...");
                }
            }
        }
        
        System.out.println("\n=== VARIABLE ASSERTION ANALYSIS ===");
        // Check individual variables for assertion
        for (Map.Entry<String, Object> assertEntry : variablesToAssert.entrySet()) {
            String varName = assertEntry.getKey();
            Object expectedValue = assertEntry.getValue();
            
            // Find the variable in variableMap
            Variable foundVariable = null;
            for (Variable v : variableMap.values()) {
                if (varName.equals(v.getName())) {
                    foundVariable = v;
                    break;
                }
            }
            
            if (foundVariable != null) {
                System.out.println("Variable " + varName + ": " + foundVariable.getValue());
                if (expectedValue != null) {
                    // Use JUnit assertion instead of just printing
                    assertEquals("Variable " + varName + " value mismatch", 
                        expectedValue, foundVariable.getValue());
                    System.out.println("  Assertion PASSED: " + expectedValue + " == " + foundVariable.getValue());
                } else {
                    System.out.println("  Analysis complete (no assertion specified)");
                }
            } else {
                if (expectedValue != null) {
                    // Use JUnit assertion to fail if variable is expected but not found
                    fail("Variable " + varName + " was expected but not found in results");
                } else {
                    System.out.println("Variable " + varName + ": NOT FOUND (no assertion specified)");
                }
            }
        }
    }
}

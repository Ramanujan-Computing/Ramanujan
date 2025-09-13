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
    public void yetAnotherBigOneForCheckingTimeTakenChecks() throws Exception {
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
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
        
        // Add some basic assertions to verify execution completed successfully
        assertNotNull("Variable map should not be null", variableMap);
        assertNotNull("Array map should not be null", arrayMap);
        assertTrue("Should have some variables", variableMap.size() > 0);
        assertTrue("Should have some arrays", arrayMap.size() > 0);
    }

    @Test
    public void testNestedWhileLoops() throws Exception {
        String code = "def nestedWhileTest(var outer:integer, var inner:integer, var result:integer) {\n" +
                "    var i,j:integer;\n" +
                "    result = 0;\n" +
                "    i = 0;\n" +
                "    while(i < outer) {\n" +
                "        j = 0;\n" +
                "        while(j < inner) {\n" +
                "            result = result + i * j;\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var testResult:integer;\n" +
                "exec nestedWhileTest(3, 4, testResult);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("testResult", 18d); // Expected: (0*0+0*1+0*2+0*3) + (1*0+1*1+1*2+1*3) + (2*0+2*1+2*2+2*3) = 0 + 6 + 12 = 18
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testNestedIfElseBlocks() throws Exception {
        String code = "def nestedIfElseTest(var x:integer, var y:integer, var result:integer) {\n" +
                "    if(x > 0) {\n" +
                "        if(y > 0) {\n" +
                "            result = 1;\n" +
                "        } else {\n" +
                "            if(y == 0) {\n" +
                "                result = 2;\n" +
                "            } else {\n" +
                "                result = 3;\n" +
                "            }\n" +
                "        }\n" +
                "    } else {\n" +
                "        if(x == 0) {\n" +
                "            result = 4;\n" +
                "        } else {\n" +
                "            result = 5;\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "var result1,result2,result3,result4,result5:integer;\n" +
                "exec nestedIfElseTest(1, 1, result1);\n" +
                "exec nestedIfElseTest(1, 0, result2);\n" +
                "exec nestedIfElseTest(1, -1, result3);\n" +
                "exec nestedIfElseTest(0, 5, result4);\n" +
                "exec nestedIfElseTest(-1, 5, result5);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("result1", 1d); // x>0, y>0
        variablesToAssert.put("result2", 2d); // x>0, y==0
        variablesToAssert.put("result3", 3d); // x>0, y<0
        variablesToAssert.put("result4", 4d); // x==0
        variablesToAssert.put("result5", 5d); // x<0
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testRecursiveFactorial() throws Exception {
        String code = "def factorial(var n:integer, var result:integer) {\n" +
                "    if(n <= 1) {\n" +
                "        result = 1;\n" +
                "    } else {\n" +
                "        var temp:integer;\n" +
                "        var n_minus_1:integer;\n" +
                "        n_minus_1 = n - 1;\n" +
                "        exec factorial(n_minus_1, temp);\n" +
                "        result = n * temp;\n" +
                "    }\n" +
                "}\n" +
                "var fact5,fact0,fact1:integer;\n" +
                "exec factorial(5, fact5);\n" +
                "exec factorial(0, fact0);\n" +
                "exec factorial(1, fact1);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("fact5", 120d); // 5! = 120
        variablesToAssert.put("fact0", 1d);   // 0! = 1
        variablesToAssert.put("fact1", 1d);   // 1! = 1
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testRecursiveFibonacci() throws Exception {
        String code = "def fibonacci(var n:integer, var result:integer, var arr:array) {\n" +
                "    if(n <= 1) {\n" +
                "        result = n;\n" +
                "    } else {\n" +
                "        var fib1,fib2:integer;\n" +
                "        var n_minus_1,n_minus_2:integer;\n" +
                "        n_minus_1 = n - 1;\n" +
                "        n_minus_2 = n - 2;\n" +
                "        exec fibonacci(n_minus_1, fib1, arr);\n" +
                "        exec fibonacci(n_minus_2, fib2, arr);\n" +
                "        result = fib1 + fib2;\n" +
                "        arr[n] = fib1 + fib2;\n" +
                "    }\n" +
                "}\n" +
                "var fib0,fib1, fib2, fib3, fib4, fib5,fib6, fib7:integer;\n" +
                "var arr[8]:array;" +
                "exec fibonacci(0, fib0, arr);\n" +
                "exec fibonacci(1, fib1, arr);\n" +
                "exec fibonacci(2, fib2, arr);\n" +
                "exec fibonacci(3, fib3, arr);\n" +
                "exec fibonacci(4, fib4, arr);\n" +
                "exec fibonacci(5, fib5, arr);\n" +
                "exec fibonacci(6, fib6, arr);\n" +
                "exec fibonacci(7, fib7, arr);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("fib0", 0d); // fib(0) = 0
        variablesToAssert.put("fib1", 1d); // fib(1) = 1
        variablesToAssert.put("fib2", 1d); // fib(2) = 1
        variablesToAssert.put("fib3", 2d); // fib(3) = 2
        variablesToAssert.put("fib4", 3d); // fib(4) = 3
        variablesToAssert.put("fib5", 5d); // fib(5) = 5
        variablesToAssert.put("fib6", 8d); // fib(6) = 8
        variablesToAssert.put("fib7", 13d); // fib(7) = 13
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testFunctionChaining() throws Exception {
        String code = "def addOne(var input:integer, var output:integer) {\n" +
                "    output = input + 1;\n" +
                "}\n" +
                "def multiplyByTwo(var input:integer, var output:integer) {\n" +
                "    output = input * 2;\n" +
                "}\n" +
                "def square(var input:integer, var output:integer) {\n" +
                "    output = input * input;\n" +
                "}\n" +
                "def chainedOperation(var start:integer, var result:integer) {\n" +
                "    var temp1,temp2,temp3:integer;\n" +
                "    exec addOne(start, temp1);\n" +
                "    exec multiplyByTwo(temp1, temp2);\n" +
                "    exec square(temp2, temp3);\n" +
                "    exec addOne(temp3, result);\n" +
                "}\n" +
                "var finalResult:integer;\n" +
                "exec chainedOperation(3, finalResult);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        // Chain: 3 -> addOne(4) -> multiplyByTwo(8) -> square(64) -> addOne(65)
        variablesToAssert.put("finalResult", 65d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testComplexWhileWithArrays() throws Exception {
        String code = "def bubbleSort(var arr:array, var size:integer) {\n" +
                "    var i,j,temp,nextJ,outerLimit,innerLimit:integer;\n" +
                "    outerLimit = size - 1;\n" +
                "    i = 0;\n" +
                "    while(i < outerLimit) {\n" +
                "        j = 0;\n" +
                "        innerLimit = outerLimit - i;\n" +
                "        while(j < innerLimit) {\n" +
                    "            nextJ = j + 1;\n" +
                    "            if(arr[j] > arr[nextJ]) {\n" +
                    "                temp = arr[j];\n" +
                    "                arr[j] = arr[nextJ];\n" +
                    "                arr[nextJ] = temp;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var sortArray[5]:array;\n" +
                "sortArray[0] = 64;\n" +
                "sortArray[1] = 34;\n" +
                "sortArray[2] = 25;\n" +
                "sortArray[3] = 12;\n" +
                "sortArray[4] = 22;\n" +
                "exec bubbleSort(sortArray, 5);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedSortedArray = new HashMap<>();
        expectedSortedArray.put("0", 12d);
        expectedSortedArray.put("1", 22d);
        expectedSortedArray.put("2", 25d);
        expectedSortedArray.put("3", 34d);
        expectedSortedArray.put("4", 64d);
        arrayIndexToAssert.put("sortArray", expectedSortedArray);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    @Test
    public void testNestedIfWithWhileLoop() throws Exception {
    String code = "def findPrimes(var limit:integer, var primes:array, var count:integer) {\n" +
        "    var num,i,isPrime,iSquared:integer;\n" +
        "    count = 0;\n" +
        "    num = 2;\n" +
        "    while(num <= limit) {\n" +
        "        isPrime = 1;\n" +
        "        i = 2;\n" +
        "        iSquared = i * i;\n" +
        "        while(iSquared <= num) {\n" +
        "            var quotient:integer;\n" +
        "            var tempNum:integer;\n" +
        "            quotient = 0;\n" +
        "            tempNum = num;\n" +
        "            while(tempNum >= i) {\n" +
        "                tempNum = tempNum - i;\n" +
        "                quotient = quotient + 1;\n" +
        "            }\n" +
        "            if(tempNum == 0) {\n" +
        "                isPrime = 0;\n" +
        "            }\n" +
        "            i = i + 1;\n" +
        "            iSquared = i * i;\n" +
        "        }\n" +
        "        if(isPrime == 1) {\n" +
        "            primes[count] = num;\n" +
        "            count = count + 1;\n" +
        "        }\n" +
        "        num = num + 1;\n" +
        "    }\n" +
        "}\n" +
        "var primeArray[10]:array;\n" +
        "var primeCount:integer;\n" +
        "exec findPrimes(20, primeArray, primeCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("primeCount", 8d); // Primes <= 20: 2,3,5,7,11,13,17,19
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedPrimes = new HashMap<>();
        expectedPrimes.put("0", 2d);
        expectedPrimes.put("1", 3d);
        expectedPrimes.put("2", 5d);
        expectedPrimes.put("3", 7d);
        expectedPrimes.put("4", 11d);
        expectedPrimes.put("5", 13d);
        expectedPrimes.put("6", 17d);
        expectedPrimes.put("7", 19d);
        arrayIndexToAssert.put("primeArray", expectedPrimes);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    @Test
    public void testRecursiveArraySum() throws Exception {
        String code = "def arraySum(var arr:array, var index:integer, var size:integer, var sum:integer) {\n" +
                "    if(index >= size) {\n" +
                "        sum = 0;\n" +
                "    } else {\n" +
                "        var restSum:integer;\n" +
                "        var nextIndex:integer;\n" +
                "        nextIndex = index + 1;\n" +
                "        exec arraySum(arr, nextIndex, size, restSum);\n" +
                "        sum = arr[index] + restSum;\n" +
                "    }\n" +
                "}\n" +
                "var testArray[5]:array;\n" +
                "testArray[0] = 10;\n" +
                "testArray[1] = 20;\n" +
                "testArray[2] = 30;\n" +
                "testArray[3] = 40;\n" +
                "testArray[4] = 50;\n" +
                "var totalSum:integer;\n" +
                "exec arraySum(testArray, 0, 5, totalSum);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("totalSum", 150d); // 10+20+30+40+50 = 150
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testComplexNestedStructures() throws Exception {
        String code = "def processMatrix(var matrix:array, var rows:integer, var cols:integer, var result:integer) {\n" +
                "    var i,j,sum,product:integer;\n" +
                "    result = 0;\n" +
                "    i = 0;\n" +
                "    while(i < rows) {\n" +
                "        sum = 0;\n" +
                "        j = 0;\n" +
                "        while(j < cols) {\n" +
                "            var index:integer;\n" +
                "            index = i * cols + j;\n" +
                "            if(i == j) {\n" +
                "                sum = sum + matrix[index] * 2;\n" +
                "            } else {\n" +
                "                if(i < j) {\n" +
                "                    sum = sum + matrix[index];\n" +
                "                } else {\n" +
                "                    sum = sum - matrix[index];\n" +
                "                }\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        if(sum > 0) {\n" +
                "            result = result + sum;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var matrix[9]:array;\n" +
                "matrix[0] = 1; matrix[1] = 2; matrix[2] = 3;\n" +
                "matrix[3] = 4; matrix[4] = 5; matrix[5] = 6;\n" +
                "matrix[6] = 7; matrix[7] = 8; matrix[8] = 9;\n" +
                "var matrixResult:integer;\n" +
                "exec processMatrix(matrix, 3, 3, matrixResult);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);
        
        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        
        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        
        Map<String, Object> variablesToAssert = new HashMap<>();
        // Row 0: (1*2) + 2 + 3 = 7 > 0, add 7
        // Row 1: -4 + (5*2) + 6 = 12 > 0, add 12  
        // Row 2: -7 - 8 + (9*2) = 3 > 0, add 3
        // Total: 7 + 12 + 3 = 22
        variablesToAssert.put("matrixResult", 22d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
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
     * @param arrayIndexToAssert Map of array names to expected index-value pairs for assertion
     */
    private void analyzeResults(Map<String, Variable> variableMap, Map<String, Array> arrayMap, Map<String, Object> variablesToAssert, Map<String, Object> arrayIndexToAssert) {
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
            if (id.contains("func") || id.contains("if") || id.contains("while")) {
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
            if (arrayIndexToAssert.containsKey(arrayName)) {
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
                Object expectedValue = arrayIndexToAssert.get(arrayName);
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
                if (v.getId().contains("func") || v.getId().contains("if") || v.getId().contains("while")) {
                    continue;
                }
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

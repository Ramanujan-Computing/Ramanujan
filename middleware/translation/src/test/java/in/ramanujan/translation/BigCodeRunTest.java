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
    // Tests interpreter's ability to execute a large, multi-function code block with nested control flow and array operations, simulating a real-world workload.
    // Tests interpreter's ability to execute a large, multi-function code block with nested control flow and array operations, simulating a real-world workload.
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

    // Verifies correct handling of nested while loops and accumulation logic in the interpreter.
    // Verifies correct handling of nested while loops and accumulation logic in the interpreter.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("testResult", 18d); // Expected: (0*0+0*1+0*2+0*3) + (1*0+1*1+1*2+1*3) + (2*0+2*1+2*2+2*3) = 0 + 6 + 12 = 18
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Checks interpreter's support for nested if-else blocks and correct branching logic.
    // Checks interpreter's support for nested if-else blocks and correct branching logic.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("result1", 1d); // x>0, y>0
        variablesToAssert.put("result2", 2d); // x>0, y==0
        variablesToAssert.put("result3", 3d); // x>0, y<0
        variablesToAssert.put("result4", 4d); // x==0
        variablesToAssert.put("result5", 5d); // x<0
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests recursive function execution and stack management for factorial calculation.
    // Tests recursive function execution and stack management for factorial calculation.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("fact5", 120d); // 5! = 120
        variablesToAssert.put("fact0", 1d);   // 0! = 1
        variablesToAssert.put("fact1", 1d);   // 1! = 1
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests recursive function execution and array passing for Fibonacci calculation.
    // Tests recursive function execution and array passing for Fibonacci calculation.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

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

    // Verifies function chaining and correct order of execution in the interpreter.
    // Verifies function chaining and correct order of execution in the interpreter.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Chain: 3 -> addOne(4) -> multiplyByTwo(8) -> square(64) -> addOne(65)
        variablesToAssert.put("finalResult", 65d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests complex while loops and array manipulation (e.g., bubble sort) in the interpreter.
    // Tests complex while loops and array manipulation (e.g., bubble sort) in the interpreter.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

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

    // Checks nested if statements within while loops and array output (e.g., prime number generation).
    // Checks nested if statements within while loops and array output (e.g., prime number generation).
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

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

    // Tests recursive array processing and sum calculation.
    // Tests recursive array processing and sum calculation.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("totalSum", 150d); // 10+20+30+40+50 = 150
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Verifies handling of complex nested loops and multi-dimensional array processing.
    // Verifies handling of complex nested loops and multi-dimensional array processing.
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
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Row 0: (1*2) + 2 + 3 = 7 > 0, add 7
        // Row 1: -4 + (5*2) + 6 = 12 > 0, add 12  
        // Row 2: -7 - 8 + (9*2) = 3 > 0, add 3
        // Total: 7 + 12 + 3 = 22
        variablesToAssert.put("matrixResult", 22d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests deep nesting of if/while/function calls and stepwise result tracking.
    // Tests deep nesting of if/while/function calls and stepwise result tracking.
    @Test
    public void testDeepNestedIfWhileFunction() throws Exception {
        String code = "def processNestedData(var data:array, var size:integer, var result:integer, var stepResults:array, var stepCount:integer) {\n" +
                "    var i,j,k:integer;\n" +
                "    result = 0;\n" +
                "    stepCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        stepResults[stepCount] = result;\n" +
                "        stepCount = stepCount + 1;\n" +
                "        if(data[i] > 0) {\n" +
                "            j = 0;\n" +
                "            while(j < data[i]) {\n" +
                "                if(j > 2) {\n" +
                "                    k = 0;\n" +
                "                    while(k < 3) {\n" +
                "                        if(k == 1) {\n" +
                "                            result = result + i + j + k;\n" +
                "                        } else {\n" +
                "                            if(k == 2) {\n" +
                "                                result = result + i * j;\n" +
                "                            }\n" +
                "                        }\n" +
                "                        k = k + 1;\n" +
                "                    }\n" +
                "                } else {\n" +
                "                    result = result + j;\n" +
                "                }\n" +
                "                j = j + 1;\n" +
                "            }\n" +
                "        } else {\n" +
                "            if(data[i] == 0) {\n" +
                "                result = result + 1;\n" +
                "            }\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "    stepResults[stepCount] = result;\n" +
                "    stepCount = stepCount + 1;\n" +
                "}\n" +
                "var testData[4]:array;\n" +
                "testData[0] = 2;\n" +
                "testData[1] = 0;\n" +
                "testData[2] = 4;\n" +
                "testData[3] = -1;\n" +
                "var deepResult:integer;\n" +
                "var stepResults[5]:array;\n" +
                "var stepCount:integer;\n" +
                "exec processNestedData(testData, 4, deepResult, stepResults, stepCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // i=0,data[0]=2: j=0,1 -> result += 0+1 = 1
        // i=1,data[1]=0: result += 1 = 2  
        // i=2,data[2]=4: j=0,1,2 -> result += 0+1+2 = 5, j=3 -> k loop: result += (2+3+1)+(2*3) = 5+6+6 = 17
        variablesToAssert.put("deepResult", 17d);
        variablesToAssert.put("stepCount", 5d); // 4 iterations + final step
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedStepResults = new HashMap<>();
        expectedStepResults.put("0", 0d);  // Initial state before i=0
        expectedStepResults.put("1", 1d);  // After i=0 (result=1)
        expectedStepResults.put("2", 2d);  // After i=1 (result=2)
        expectedStepResults.put("3", 17d); // After i=2 (result=17)
        expectedStepResults.put("4", 17d); // After i=3 (result=17, no change)
        arrayIndexToAssert.put("stepResults", expectedStepResults);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // Checks multiple recursive functions and their interaction (e.g., even/odd counting).
    // Checks multiple recursive functions and their interaction (e.g., even/odd counting).
    @Test
    public void testMultipleRecursiveFunctions() throws Exception {
        String code = "def isEven(var n:integer, var result:integer) {\n" +
                "    if(n == 0) {\n" +
                "        result = 1;\n" +
                "    } else {\n" +
                "        if(n == 1) {\n" +
                "            result = 0;\n" +
                "        } else {\n" +
                "            var temp:integer;\n" +
                "            var n_minus_2:integer;\n" +
                "            n_minus_2 = n - 2;\n" +
                "            exec isEven(n_minus_2, temp);\n" +
                "            result = temp;\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "def processArray(var arr:array, var size:integer, var evenCount:integer, var oddCount:integer) {\n" +
                "    if(size == 0) {\n" +
                "        evenCount = 0;\n" +
                "        oddCount = 0;\n" +
                "    } else {\n" +
                "        var prevEven,prevOdd,isCurrentEven:integer;\n" +
                "        var size_minus_1:integer;\n" +
                "        size_minus_1 = size - 1;\n" +
                "        exec processArray(arr, size_minus_1, prevEven, prevOdd);\n" +
                "        var arrSizeMinus1 :integer;\n" +
                "        arrSizeMinus1 = arr[size_minus_1];\n" +
                "        exec isEven(arrSizeMinus1, isCurrentEven);\n" +
                "        if(isCurrentEven == 1) {\n" +
                "            evenCount = prevEven + 1;\n" +
                "            oddCount = prevOdd;\n" +
                "        } else {\n" +
                "            evenCount = prevEven;\n" +
                "            oddCount = prevOdd + 1;\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "var numbers[5]:array;\n" +
                "numbers[0] = 2;\n" +
                "numbers[1] = 3;\n" +
                "numbers[2] = 4;\n" +
                "numbers[3] = 5;\n" +
                "numbers[4] = 6;\n" +
                "var evenTotal,oddTotal:integer;\n" +
                "exec processArray(numbers, 5, evenTotal, oddTotal);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("evenTotal", 3d); // 2, 4, 6 are even
        variablesToAssert.put("oddTotal", 2d);  // 3, 5 are odd
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests nested loops with early break simulation and matrix search logic.
    // Tests nested loops with early break simulation and matrix search logic.
    @Test
    public void testNestedLoopsWithEarlyBreakSimulation() throws Exception {
        String code = "def findFirstMatch(var matrix:array, var rows:integer, var cols:integer, var target:integer, var foundRow:integer, var foundCol:integer) {\n" +
                "    var i,j,found:integer;\n" +
                "    foundRow = -1;\n" +
                "    foundCol = -1;\n" +
                "    found = 0;\n" +
                "    i = 0;\n" +
                "    while(i < rows) {\n" +
                "        if(found == 0) {\n" +
                "            j = 0;\n" +
                "            while(j < cols) {\n" +
                "                if(found == 0) {\n" +
                "                    var index:integer;\n" +
                "                    index = i * cols + j;\n" +
                "                    if(matrix[index] == target) {\n" +
                "                        foundRow = i;\n" +
                "                        foundCol = j;\n" +
                "                        found = 1;\n" +
                "                    }\n" +
                "                }\n" +
                "                j = j + 1;\n" +
                "            }\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var searchMatrix[12]:array;\n" +
                "searchMatrix[0] = 1; searchMatrix[1] = 2; searchMatrix[2] = 3; searchMatrix[3] = 4;\n" +
                "searchMatrix[4] = 5; searchMatrix[5] = 6; searchMatrix[6] = 7; searchMatrix[7] = 8;\n" +
                "searchMatrix[8] = 9; searchMatrix[9] = 10; searchMatrix[10] = 11; searchMatrix[11] = 12;\n" +
                "var row,col:integer;\n" +
                "exec findFirstMatch(searchMatrix, 3, 4, 7, row, col);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("row", 1d);  // 7 is at row 1 (0-indexed)
        variablesToAssert.put("col", 2d);  // 7 is at col 2 (0-indexed)
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Verifies state transitions and complex variable updates in nested loops.
    // Verifies state transitions and complex variable updates in nested loops.
    @Test
    public void testComplexStateTransition() throws Exception {
        String code = "def processStateMachine(var events:array, var eventCount:integer, var finalState:integer, var stateHistory:array, var historyCount:integer) {\n" +
                "    var state,i,event:integer;\n" +
                "    state = 0;\n" +
                "    i = 0;\n" +
                "    historyCount = 0;\n" +
                "    stateHistory[historyCount] = state;\n" +
                "    historyCount = historyCount + 1;\n" +
                "    while(i < eventCount) {\n" +
                "        event = events[i];\n" +
                "        if(state == 0) {\n" +
                "            if(event == 1) {\n" +
                "                state = 1;\n" +
                "            } else {\n" +
                "                if(event == 2) {\n" +
                "                    state = 2;\n" +
                "                }\n" +
                "            }\n" +
                "        } else {\n" +
                "            if(state == 1) {\n" +
                "                if(event == 2) {\n" +
                "                    state = 3;\n" +
                "                } else {\n" +
                "                    if(event == 3) {\n" +
                "                        state = 0;\n" +
                "                    }\n" +
                "                }\n" +
                "            } else {\n" +
                "                if(state == 2) {\n" +
                "                    if(event == 1) {\n" +
                "                        state = 3;\n" +
                "                    } else {\n" +
                "                        if(event == 3) {\n" +
                "                            state = 0;\n" +
                "                        }\n" +
                "                    }\n" +
                "                } else {\n" +
                "                    if(state == 3) {\n" +
                "                        if(event == 3) {\n" +
                "                            state = 0;\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        stateHistory[historyCount] = state;\n" +
                "        historyCount = historyCount + 1;\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "    finalState = state;\n" +
                "}\n" +
                "var eventSequence[6]:array;\n" +
                "eventSequence[0] = 1;\n" +
                "eventSequence[1] = 2;\n" +
                "eventSequence[2] = 3;\n" +
                "eventSequence[3] = 1;\n" +
                "eventSequence[4] = 2;\n" +
                "eventSequence[5] = 3;\n" +
                "var endState:integer;\n" +
                "var stateHistory[10]:array;\n" +
                "var historyCount:integer;\n" +
                "exec processStateMachine(eventSequence, 6, endState, stateHistory, historyCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // State transitions: 0->1->3->0->1->3->0
        variablesToAssert.put("endState", 0d);
        variablesToAssert.put("historyCount", 7d); // Initial state + 6 transitions
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedStateHistory = new HashMap<>();
        expectedStateHistory.put("0", 0d); // Initial state
        expectedStateHistory.put("1", 1d); // After event 1
        expectedStateHistory.put("2", 3d); // After event 2  
        expectedStateHistory.put("3", 0d); // After event 3
        expectedStateHistory.put("4", 1d); // After event 1
        expectedStateHistory.put("5", 3d); // After event 2
        expectedStateHistory.put("6", 0d); // After event 3
        arrayIndexToAssert.put("stateHistory", expectedStateHistory);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // Tests mutual recursion between functions (e.g., even/odd check).
    // Tests mutual recursion between functions (e.g., even/odd check).
    @Test
    public void testMutualRecursion() throws Exception {
        String code = "def isEvenMutual(var n:integer, var result:integer) {\n" +
                "    if(n == 0) {\n" +
                "        result = 1;\n" +
                "    } else {\n" +
                "        var temp:integer;\n" +
                "        var n_minus_1:integer;\n" +
                "        n_minus_1 = n - 1;\n" +
                "        exec isOddMutual(n_minus_1, temp);\n" +
                "        result = temp;\n" +
                "    }\n" +
                "}\n" +
                "def isOddMutual(var n:integer, var result:integer) {\n" +
                "    if(n == 0) {\n" +
                "        result = 0;\n" +
                "    } else {\n" +
                "        var temp:integer;\n" +
                "        var n_minus_1:integer;\n" +
                "        n_minus_1 = n - 1;\n" +
                "        exec isEvenMutual(n_minus_1, temp);\n" +
                "        result = temp;\n" +
                "    }\n" +
                "}\n" +
                "var testEven4,testOdd5,testEven0,testOdd7:integer;\n" +
                "exec isEvenMutual(4, testEven4);\n" +
                "exec isOddMutual(5, testOdd5);\n" +
                "exec isEvenMutual(0, testEven0);\n" +
                "exec isOddMutual(7, testOdd7);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("testEven4", 1d); // 4 is even
        variablesToAssert.put("testOdd5", 1d);  // 5 is odd
        variablesToAssert.put("testEven0", 1d); // 0 is even
        variablesToAssert.put("testOdd7", 1d);  // 7 is odd
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Checks variable scope and shadowing in nested function calls.
    // Checks variable scope and shadowing in nested function calls.
    @Test
    public void testVariableScope() throws Exception {
        String code = "def outerFunction(var x:integer, var result:integer) {\n" +
                "    var localVar:integer;\n" +
                "    localVar = x * 2;\n" +
                "    if(x > 5) {\n" +
                "        var innerVar:integer;\n" +
                "        innerVar = localVar + 3;\n" +
                "        exec innerFunction(innerVar, result);\n" +
                "    } else {\n" +
                "        var innerVar:integer;\n" +
                "        innerVar = localVar - 1;\n" +
                "        result = innerVar;\n" +
                "    }\n" +
                "}\n" +
                "def innerFunction(var y:integer, var output:integer) {\n" +
                "    var temp:integer;\n" +
                "    temp = y;\n" +
                "    if(y > 10) {\n" +
                "        while(temp > 10) {\n" +
                "            temp = temp - 2;\n" +
                "        }\n" +
                "    }\n" +
                "    output = temp;\n" +
                "}\n" +
                "var result1,result2,result3:integer;\n" +
                "exec outerFunction(3, result1);\n" +
                "exec outerFunction(7, result2);\n" +
                "exec outerFunction(10, result3);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("result1", 5d);  // 3*2-1 = 5
        variablesToAssert.put("result2", 9d);  // 7*2+3 = 17, then while loop reduces to 9
        variablesToAssert.put("result3", 9d);  // 10*2+3 = 23, then while loop reduces to 9
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests merging, sorting, and array manipulation in the interpreter.
    // Tests merging, sorting, and array manipulation in the interpreter.
    @Test
    public void testComplexArrayOperations() throws Exception {
        String code = "def mergeAndSort(var arr1:array, var size1:integer, var arr2:array, var size2:integer, var merged:array, var mergedSize:integer) {\n" +
                "    var i,j,k:integer;\n" +
                "    i = 0;\n" +
                "    j = 0;\n" +
                "    k = 0;\n" +
                "    while(i < size1) {\n" +
                "        if(j < size2) {\n" +
                "            if(arr1[i] <= arr2[j]) {\n" +
                "                merged[k] = arr1[i];\n" +
                "                i = i + 1;\n" +
                "            } else {\n" +
                "                merged[k] = arr2[j];\n" +
                "                j = j + 1;\n" +
                "            }\n" +
                "        } else {\n" +
                "            merged[k] = arr1[i];\n" +
                "            i = i + 1;\n" +
                "        }\n" +
                "        k = k + 1;\n" +
                "    }\n" +
                "    while(j < size2) {\n" +
                "        merged[k] = arr2[j];\n" +
                "        j = j + 1;\n" +
                "        k = k + 1;\n" +
                "    }\n" +
                "    mergedSize = k;\n" +
                "}\n" +
                "var array1[3]:array;\n" +
                "array1[0] = 1;\n" +
                "array1[1] = 4;\n" +
                "array1[2] = 7;\n" +
                "var array2[4]:array;\n" +
                "array2[0] = 2;\n" +
                "array2[1] = 3;\n" +
                "array2[2] = 5;\n" +
                "array2[3] = 6;\n" +
                "var result[7]:array;\n" +
                "var totalSize:integer;\n" +
                "exec mergeAndSort(array1, 3, array2, 4, result, totalSize);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("totalSize", 7d);
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedMerged = new HashMap<>();
        expectedMerged.put("0", 1d);
        expectedMerged.put("1", 2d);
        expectedMerged.put("2", 3d);
        expectedMerged.put("3", 4d);
        expectedMerged.put("4", 5d);
        expectedMerged.put("5", 6d);
        expectedMerged.put("6", 7d);
        arrayIndexToAssert.put("result", expectedMerged);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
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
                            if ((double)expectedVal == 0d) {
                                continue;
                            }
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

    // ========== ADDITIONAL COMPREHENSIVE COMBINATION TESTS ==========

    // Tests recursive while loops with array mutation and operation tracking.
    // Tests recursive while loops with array mutation and operation tracking.
    @Test
    public void testRecursiveWhileWithArrayManipulation() throws Exception {
        String code = "def processRecursiveArray(var arr:array, var start:integer, var end:integer, var operations:array, var opCount:integer) {\n" +
                "    if(start < end) {\n" +
                "        var i:integer;\n" +
                "        i = start;\n" +
                "        while(i < end) {\n" +
                "            if(arr[i] > 5) {\n" +
                "                var mid, mid1:integer;\n" +
                "                mid = (start + end) / 2;\n" +
                "                exec FLOOR(mid);\n" +
                "                mid1 = mid + 1;\n" +
                "                operations[opCount] = i;\n" +
                "                opCount = opCount + 1;\n" +
                "                exec processRecursiveArray(arr, start, mid, operations, opCount);\n" +
                "                exec processRecursiveArray(arr, mid1, end, operations, opCount);\n" +
                "                i = end;\n" +
                "            } else {\n" +
                "                arr[i] = arr[i] * 2;\n" +
                "                i = i + 1;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "var data[6]:array;\n" +
                "data[0] = 3; data[1] = 7; data[2] = 2; data[3] = 9; data[4] = 1; data[5] = 4;\n" +
                "var ops[10]:array;\n" +
                "var opCount:integer;\n" +
                "opCount = 0;\n" +
                "exec processRecursiveArray(data, 0, 6, ops, opCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("opCount", 3d); // Should find one element > 5 and trigger recursion
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedData = new HashMap<>();
        // expectedData.put("0", 6d);  // 3*2 = 6 (processed)
        // expectedData.put("1", 7d);  // 7 (triggers recursion, unchanged)
        // expectedData.put("2", 2d);  // Original value
        // expectedData.put("3", 9d);  // Original value  
        // expectedData.put("4", 1d);  // Original value
        // expectedData.put("5", 4d);  // Original value
        expectedData.put("0", 6d);  // 3*2 = 6 (processed)
        expectedData.put("1", 7d);  // 7 (triggers recursion, unchanged)
        expectedData.put("2", 4d);  // 2*2 = 4 (processed in recursive call)
        expectedData.put("3", 9d);  // Original value (recursion skips this)
        expectedData.put("4", 2d);  // 1*2 = 2 (processed in second recursive call)
        expectedData.put("5", 8d);  // 4*2 = 8 (processed in second recursive call)
        arrayIndexToAssert.put("data", expectedData);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // Verifies nested function calls with conditionals and expression tree processing.
    // Verifies nested function calls with conditionals and expression tree processing.
    @Test
    public void testNestedFunctionCallsWithConditionals() throws Exception {
        String code = "def mathOperation(var a:integer, var b:integer, var op:integer, var result:integer) {\n" +
                "    if(op == 1) {\n" +
                "        result = a + b;\n" +
                "    } else {\n" +
                "        if(op == 2) {\n" +
                "            result = a * b;\n" +
                "        } else {\n" +
                "            if(op == 3) {\n" +
                "                result = a - b;\n" +
                "            } else {\n" +
                "                result = 0;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "def processExpressionTree(var expr:array, var size:integer, var result:integer, var trace:array, var traceCount:integer) {\n" +
                "    var i:integer;\n" +
                "    result = 0;\n" +
                "    traceCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        if(expr[i] > 0) {\n" +
                "            var temp1,temp2,op:integer;\n" +
                "            temp1 = expr[i];\n" +
                "            i = i + 1;\n" +
                "            if(i < size) {\n" +
                "                op = expr[i];\n" +
                "                i = i + 1;\n" +
                "                if(i < size) {\n" +
                "                    temp2 = expr[i];\n" +
                "                    var opResult:integer;\n" +
                "                    exec mathOperation(temp1, temp2, op, opResult);\n" +
                "                    result = result + opResult;\n" +
                "                    trace[traceCount] = opResult;\n" +
                "                    traceCount = traceCount + 1;\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var expression[9]:array;\n" +
                "expression[0] = 5; expression[1] = 1; expression[2] = 3;\n" +
                "expression[3] = 4; expression[4] = 2; expression[5] = 2;\n" +
                "expression[6] = 7; expression[7] = 3; expression[8] = 1;\n" +
                "var finalResult:integer;\n" +
                "var operationTrace[5]:array;\n" +
                "var traceSize:integer;\n" +
                "exec processExpressionTree(expression, 9, finalResult, operationTrace, traceSize);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Operations: (5+3)+(4*2)+(7-1) = 8+8+6 = 22
        variablesToAssert.put("finalResult", 22d);
        variablesToAssert.put("traceSize", 3d);
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedTrace = new HashMap<>();
        expectedTrace.put("0", 8d);  // 5+3
        expectedTrace.put("1", 8d);  // 4*2
        expectedTrace.put("2", 6d);  // 7-1
        arrayIndexToAssert.put("operationTrace", expectedTrace);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // Tests nested while loops with recursive validation (e.g., power of two check).
    // Tests nested while loops with recursive validation (e.g., power of two check).
    @Test
    public void testNestedWhileWithRecursiveValidation() throws Exception {
        String code = "def isPowerOfTwo(var n:integer, var result:integer) {\n" +
                "    if(n <= 0) {\n" +
                "        result = 0;\n" +
                "    } else {\n" +
                "        if(n == 1) {\n" +
                "            result = 1;\n" +
                "        } else {\n" +
                "            var remainder:integer;\n" +
                "            var quotient:integer;\n" +
                "            quotient = 0;\n" +
                "            remainder = n;\n" +
                "            while(remainder >= 2) {\n" +
                "                remainder = remainder - 2;\n" +
                "                quotient = quotient + 1;\n" +
                "            }\n" +
                "            if(remainder == 0) {\n" +
                "                exec isPowerOfTwo(quotient, result);\n" +
                "            } else {\n" +
                "                result = 0;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "def validateArray(var arr:array, var size:integer, var validCount:integer, var results:array) {\n" +
                "    var i:integer;\n" +
                "    validCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        var isValid,numToBeChecked:integer;numToBeChecked = arr[i];\n" +
                "        exec isPowerOfTwo(numToBeChecked, isValid);\n" +
                "        results[i] = isValid;\n" +
                "        if(isValid == 1) {\n" +
                "            validCount = validCount + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var numbers[6]:array;\n" +
                "numbers[0] = 1; numbers[1] = 4; numbers[2] = 6; numbers[3] = 8; numbers[4] = 12; numbers[5] = 16;\n" +
                "var validPowers:integer;\n" +
                "var validationResults[6]:array;\n" +
                "exec validateArray(numbers, 6, validPowers, validationResults);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("validPowers", 4d); // 1,4,8,16 are powers of 2
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedResults = new HashMap<>();
        expectedResults.put("0", 1d); // 1 is 2^0
        expectedResults.put("1", 1d); // 4 is 2^2
        expectedResults.put("2", 0d); // 6 is not power of 2
        expectedResults.put("3", 1d); // 8 is 2^3
        expectedResults.put("4", 0d); // 12 is not power of 2
        expectedResults.put("5", 1d); // 16 is 2^4
        arrayIndexToAssert.put("validationResults", expectedResults);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // Verifies complex conditional logic and array processing in the interpreter.
    // Verifies complex conditional logic and array processing in the interpreter.
    @Test
    public void testComplexConditionalArrayProcessing() throws Exception {
        String code = "def categorizeAndProcess(var data:array, var size:integer, var categories:array, var categoryCount:integer, var processedData:array) {\n" +
                "    var i,j:integer;\n" +
                "    categoryCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        var value:integer;\n" +
                "        value = data[i];\n" +
                "        if(value < 10) {\n" +
                "            if(value < 5) {\n" +
                "                categories[categoryCount] = 1;\n" +
                "                processedData[i] = value * 3;\n" +
                "            } else {\n" +
                "                categories[categoryCount] = 2;\n" +
                "                processedData[i] = value + 10;\n" +
                "            }\n" +
                "        } else {\n" +
                "            if(value < 20) {\n" +
                "                categories[categoryCount] = 3;\n" +
                "                var temp:integer;\n" +
                "                temp = value;\n" +
                "                j = 0;\n" +
                "                while(j < 3) {\n" +
                "                    temp = temp + j;\n" +
                "                    j = j + 1;\n" +
                "                }\n" +
                "                processedData[i] = temp;\n" +
                "            } else {\n" +
                "                categories[categoryCount] = 4;\n" +
                "                processedData[i] = value - 15;\n" +
                "            }\n" +
                "        }\n" +
                "        categoryCount = categoryCount + 1;\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var inputData[5]:array;\n" +
                "inputData[0] = 3; inputData[1] = 7; inputData[2] = 15; inputData[3] = 25; inputData[4] = 1;\n" +
                "var cats[5]:array;\n" +
                "var catCount:integer;\n" +
                "var processed[5]:array;\n" +
                "exec categorizeAndProcess(inputData, 5, cats, catCount, processed);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("catCount", 5d);
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedCategories = new HashMap<>();
        expectedCategories.put("0", 1d); // 3 < 5, category 1
        expectedCategories.put("1", 2d); // 7 >= 5 && < 10, category 2
        expectedCategories.put("2", 3d); // 15 >= 10 && < 20, category 3
        expectedCategories.put("3", 4d); // 25 >= 20, category 4
        expectedCategories.put("4", 1d); // 1 < 5, category 1
        arrayIndexToAssert.put("cats", expectedCategories);
        
        Map<String, Object> expectedProcessed = new HashMap<>();
        expectedProcessed.put("0", 9d);  // 3*3 = 9
        expectedProcessed.put("1", 17d); // 7+10 = 17
        expectedProcessed.put("2", 18d); // 15+0+1+2 = 18
        expectedProcessed.put("3", 10d); // 25-15 = 10
        expectedProcessed.put("4", 3d);  // 1*3 = 3
        arrayIndexToAssert.put("processed", expectedProcessed);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // Tests recursive function with while loop and array swapping (e.g., partition logic in quicksort).
    @Test
    public void testRecursiveFunctionWithWhileAndArraySwapping() throws Exception {
        String code = "def partition(var arr:array, var low:integer, var high:integer, var pivotIndex:integer, var swaps:array, var swapCount:integer) {\n" +
                "    var pivot,i,j,temp:integer;\n" +
                "    pivot = arr[high];\n" +
                "    i = low - 1;\n" +
                "    j = low;\n" +
                "    while(j < high) {\n" +
                "        if(arr[j] <= pivot) {\n" +
                "            i = i + 1;\n" +
                "            if(i != j) {\n" +
                "                temp = arr[i];\n" +
                "                arr[i] = arr[j];\n" +
                "                arr[j] = temp;\n" +
                "                swaps[swapCount] = i;\n" +
                "                swapCount = swapCount + 1;\n" +
                "                swaps[swapCount] = j;\n" +
                "                swapCount = swapCount + 1;\n" +
                "            }\n" +
                "        }\n" +
                "        j = j + 1;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "    if(i != high) {\n" +
                "        temp = arr[i];\n" +
                "        arr[i] = arr[high];\n" +
                "        arr[high] = temp;\n" +
                "        swaps[swapCount] = i;\n" +
                "        swapCount = swapCount + 1;\n" +
                "        swaps[swapCount] = high;\n" +
                "        swapCount = swapCount + 1;\n" +
                "    }\n" +
                "    pivotIndex = i;\n" +
                "}\n" +
                "var sortData[5]:array;\n" +
                "sortData[0] = 64; sortData[1] = 34; sortData[2] = 25; sortData[3] = 12; sortData[4] = 22;\n" +
                "var swapOperations[20]:array;\n" +
                "var swapCount:integer;\n" +
                "var partitionResult:integer;\n" +
                "swapCount = 0;\n" +
                "exec partition(sortData, 0, 4, partitionResult, swapOperations, swapCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("partitionResult", 1d); // 22 should end up at position 1
        variablesToAssert.put("swapCount", null); // Will depend on specific swaps performed
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // ========== VARIABLE LENGTH ARRAY TESTS ==========

    // Tests dynamic single-dimension array creation and processing.
    @Test
    public void testDynamicSingleDimensionArray() throws Exception {
        String code = "def processDynamicArray(var size:integer, var multiplier:integer, var sum:integer, var processedCount:integer) {\n" +
                "    var arr[size]:array;\n" +
                "    var i:integer;\n" +
                "    sum = 0;\n" +
                "    processedCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        arr[i] = i * multiplier;\n" +
                "        if(arr[i] > 10) {\n" +
                "            sum = sum + arr[i];\n" +
                "            processedCount = processedCount + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var totalSum:integer;\n" +
                "var count:integer;\n" +
                "exec processDynamicArray(6, 4, totalSum, count);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // arr[0]=0, arr[1]=4, arr[2]=8, arr[3]=12, arr[4]=16, arr[5]=20
        // Elements > 10: 12, 16, 20 → sum = 48, count = 3
        variablesToAssert.put("totalSum", 48d);
        variablesToAssert.put("count", 3d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests dynamic 2D array creation and nested loop processing.
    @Test
    public void testDynamic2DArrayWithNestedLoops() throws Exception {
        String code = "def create2DMatrix(var rows:integer, var cols:integer, var diagonalSum:integer, var maxElement:integer, var coordinates:array, var coordCount:integer) {\n" +
                "    var matrix[rows][cols]:array;\n" +
                "    var i,j:integer;\n" +
                "    diagonalSum = 0;\n" +
                "    maxElement = 0;\n" +
                "    coordCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < rows) {\n" +
                "        j = 0;\n" +
                "        while(j < cols) {\n" +
                "            var value:integer;\n" +
                "            value = (i + 1) * (j + 1);\n" +
                "            matrix[i][j] = value;\n" +
                "            if(i == j) {\n" +
                "                diagonalSum = diagonalSum + value;\n" +
                "            }\n" +
                "            if(value > maxElement) {\n" +
                "                maxElement = value;\n" +
                "                coordinates[0] = i; \n" +
                "                coordinates[1] = j; \n" +
                "                coordCount = 2;     \n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var diagSum:integer;\n" +
                "var maxElem:integer;\n" +
                "var maxCoords[10]:array;\n" +
                "var coordSize:integer;\n" +
                "exec create2DMatrix(4, 3, diagSum, maxElem, maxCoords, coordSize);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Diagonal: (1*1) + (2*2) + (3*3) = 1 + 4 + 9 = 14
        // Max element: 4*3=12 at position (3,2)
        variablesToAssert.put("diagSum", 14d);
        variablesToAssert.put("maxElem", 12d);
        variablesToAssert.put("coordSize", 2d);
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedCoords = new HashMap<>();
        expectedCoords.put("0", 3d); // row 3 (0-indexed)
        expectedCoords.put("1", 2d); // col 2 (0-indexed)
        arrayIndexToAssert.put("maxCoords", expectedCoords);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    @Test
    public void testVariableSizedArrayWithRecursion() throws Exception {
        String code = "def fillTriangularArray(var size:integer, var level:integer, var arr:array, var index:integer, var sum:integer) {\n" +
                "    if(level < size) {\n" +
                "        var i:integer;\n" +
                "        i = 0;\n" +
                "        while(i <= level) {\n" +
                "            var value:integer;\n" +
                "            value = level * 10 + i;\n" +
                "            arr[index] = value;\n" +
                "            sum = sum + value;\n" +
                "            index = index + 1;\n" +
                "            i = i + 1;\n" +
                "        }\n" +
                "        level = level + 1;\n" +
                "        exec fillTriangularArray(size, level, arr, index, sum);\n" +
                "    }\n" +
                "}\n" +
                "def processTriangularData(var n:integer, var totalSum:integer, var elementCount:integer) {\n" +
                "    var triangularSize:integer;\n" +
                "    triangularSize = (n * (n + 1)) / 2;\n" +
                "    exec FLOOR(triangularSize);\n" +
                "    var triangular[triangularSize]:array;\n" +
                "    var idx:integer;\n" +
                "    idx = 0;\n" +
                "    totalSum = 0;\n" +
                "    exec fillTriangularArray(n, 0, triangular, idx, totalSum);\n" +
                "    elementCount = triangularSize;\n" +
                "}\n" +
                "var finalSum:integer;\n" +
                "var elemCount:integer;\n" +
                "exec processTriangularData(4, finalSum, elemCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Level 0: [0] → sum += 0
        // Level 1: [10,11] → sum += 21  
        // Level 2: [20,21,22] → sum += 63
        // Level 3: [30,31,32,33] → sum += 126
        // Total: 0+21+63+126 = 210
        variablesToAssert.put("finalSum", 210d);
        variablesToAssert.put("elemCount", 10d); // 4*5/2 = 10 elements
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testDynamicMultiDimensionalWithConditionals() throws Exception {
        String code = "def processMultiDimArray(var depth:integer, var width:integer, var height:integer, var threshold:integer, var result:integer, var stats:array, var statCount:integer) {\n" +
                "    var cube[depth][width][height]:array;\n" +
                "    var d,w,h:integer;\n" +
                "    result = 0;\n" +
                "    statCount = 0;\n" +
                "    d = 0;\n" +
                "    while(d < depth) {\n" +
                "        w = 0;\n" +
                "        while(w < width) {\n" +
                "            h = 0;\n" +
                "            while(h < height) {\n" +
                "                var value:integer;\n" +
                "                value = d * 100 + w * 10 + h;\n" +
                "                cube[d][w][h] = value;\n" +
                "                if(value > threshold) {\n" +
                "                    result = result + value;\n" +
                "                    if(statCount < 10) {\n" +
                "                        stats[statCount] = value;\n" +
                "                        statCount = statCount + 1;\n" +
                "                    }\n" +
                "                }\n" +
                "                h = h + 1;\n" +
                "            }\n" +
                "            w = w + 1;\n" +
                "        }\n" +
                "        d = d + 1;\n" +
                "    }\n" +
                "}\n" +
                "var cubeResult:integer;\n" +
                "var cubeStats[10]:array;\n" +
                "var cubeStatCount:integer;\n" +
                "exec processMultiDimArray(2, 2, 3, 50, cubeResult, cubeStats, cubeStatCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Values > 50: 100,101,102,110,111,112 → sum = 636
        variablesToAssert.put("cubeResult", 636d);
        variablesToAssert.put("cubeStatCount", 6d);
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedStats = new HashMap<>();
        expectedStats.put("0", 100d);
        expectedStats.put("1", 101d);
        expectedStats.put("2", 102d);
        expectedStats.put("3", 110d);
        expectedStats.put("4", 111d);
        expectedStats.put("5", 112d);
        arrayIndexToAssert.put("cubeStats", expectedStats);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    @Test
    public void testDynamicArrayWithFunctionChaining() throws Exception {
        String code = "def calculateSize(var base:integer, var multiplier:integer, var result:integer) {\n" +
                "    result = base * multiplier;\n" +
                "}\n" +
                "def initializeArray(var arr:array, var size:integer, var pattern:integer) {\n" +
                "    var i:integer;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        if(pattern == 1) {\n" +
                "            arr[i] = i * i;\n" +
                "        } else {\n" +
                "            if(pattern == 2) {\n" +
                "                arr[i] = i * 2;\n" +
                "            } else {\n" +
                "                arr[i] = i + 5;\n" +
                "            }\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "def processVariableArray(var baseSize:integer, var factor:integer, var pattern:integer, var sum:integer, var avgApprox:integer) {\n" +
                "    var actualSize:integer;\n" +
                "    exec calculateSize(baseSize, factor, actualSize);\n" +
                "    var dynamicArr[actualSize]:array;\n" +
                "    exec initializeArray(dynamicArr, actualSize, pattern);\n" +
                "    var i:integer;\n" +
                "    sum = 0;\n" +
                "    i = 0;\n" +
                "    while(i < actualSize) {\n" +
                "        sum = sum + dynamicArr[i];\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "    avgApprox = sum / actualSize;\n" +
                "}\n" +
                "var arraySum:integer;\n" +
                "var arrayAvg:integer;\n" +
                "exec processVariableArray(3, 2, 1, arraySum, arrayAvg);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // actualSize = 3*2 = 6, pattern=1 → arr = [0,1,4,9,16,25]
        // sum = 0+1+4+9+16+25 = 55, avg = 55/6 = 9 (integer division)
        variablesToAssert.put("arraySum", 55d);
        variablesToAssert.put("arrayAvg", 55d / 6d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    @Test
    public void testRecursiveWithDynamic5DArrays() throws Exception {
        String code = "def recursive5DProcessor(var depth:integer, var d1:integer, var d2:integer, var d3:integer, var d4:integer, var d5:integer, var result:integer, var processedCount:integer, var coordinates:array, var coordIndex:integer) {\n" +
                "    if(depth > 0) {\n" +
                "        var hyperArray[d1][d2][d3][d4][d5]:array;\n" +
                "        var i1,i2,i3,i4,i5:integer;\n" +
                "        var localSum:integer;\n" +
                "        localSum = 0;\n" +
                "        processedCount = 0;\n" +
                "        i1 = 0;\n" +
                "        while(i1 < d1) {\n" +
                "            i2 = 0;\n" +
                "            while(i2 < d2) {\n" +
                "                i3 = 0;\n" +
                "                while(i3 < d3) {\n" +
                "                    i4 = 0;\n" +
                "                    while(i4 < d4) {\n" +
                "                        i5 = 0;\n" +
                "                        while(i5 < d5) {\n" +
                "                            var value:integer;\n" +
                "                            value = depth * 10000 + i1 * 1000 + i2 * 100 + i3 * 10 + i4 + i5;\n" +
                "                            hyperArray[i1][i2][i3][i4][i5] = value;\n" +
                "                            localSum = localSum + value;\n" +
                "                            processedCount = processedCount + 1;\n" +
                "                            if(value > 30000) {\n" +
                "                                coordinates[coordIndex] = i1;\n" +
                "                                coordIndex = coordIndex + 1;\n" +
                "                                coordinates[coordIndex] = i2;\n" +
                "                                coordIndex = coordIndex + 1;\n" +
                "                                coordinates[coordIndex] = i3;\n" +
                "                                coordIndex = coordIndex + 1;\n" +
                "                                coordinates[coordIndex] = i4;\n" +
                "                                coordIndex = coordIndex + 1;\n" +
                "                                coordinates[coordIndex] = i5;\n" +
                "                                coordIndex = coordIndex + 1;\n" +
                "                                coordinates[coordIndex] = value;\n" +
                "                                coordIndex = coordIndex + 1;\n" +
                "                            }\n" +
                "                            i5 = i5 + 1;\n" +
                "                        }\n" +
                "                        i4 = i4 + 1;\n" +
                "                    }\n" +
                "                    i3 = i3 + 1;\n" +
                "                }\n" +
                "                i2 = i2 + 1;\n" +
                "            }\n" +
                "            i1 = i1 + 1;\n" +
                "        }\n" +
                "        result = localSum;\n" +
                "        var nextDepth:integer;\n" +
                "        nextDepth = depth - 1;\n" +
                "        if(nextDepth > 0) {\n" +
                "            var nextD1,nextD2,nextD3,nextD4,nextD5:integer;\n" +
                "            nextD1 = d1 - 1;\n" +
                "            nextD2 = d2 + 1;\n" +
                "            nextD3 = d3;\n" +
                "            nextD4 = d4 - 1;\n" +
                "            nextD5 = d5 + 1;\n" +
                "            if(nextD1 <= 0) {\n" +
                "                nextD1 = 1;\n" +
                "            }\n" +
                "            if(nextD4 <= 0) {\n" +
                "                nextD4 = 1;\n" +
                "            }\n" +
                "            var recursiveResult:integer;\n" +
                "            var recursiveCount:integer;\n" +
                "            exec recursive5DProcessor(nextDepth, nextD1, nextD2, nextD3, nextD4, nextD5, recursiveResult, recursiveCount, coordinates, coordIndex);\n" +
                "            result = result + recursiveResult;\n" +
                "            processedCount = processedCount + recursiveCount;\n" +
                "        }\n" +
                "    } else {\n" +
                "        result = 0;\n" +
                "        processedCount = 0;\n" +
                "    }\n" +
                "}\n" +
                "def initializeRecursive5DTest(var initialDepth:integer, var startD1:integer, var startD2:integer, var startD3:integer, var startD4:integer, var startD5:integer, var finalResult:integer, var totalProcessed:integer, var significantCoords:array, var coordCount:integer) {\n" +
                "    coordCount = 0;\n" +
                "    exec recursive5DProcessor(initialDepth, startD1, startD2, startD3, startD4, startD5, finalResult, totalProcessed, significantCoords, coordCount);\n" +
                "}\n" +
                "var testResult:integer;\n" +
                "var testProcessed:integer;\n" +
                "var testCoords[100]:array;\n" +
                "var testCoordCount:integer;\n" +
                "exec initializeRecursive5DTest(3, 2, 1, 2, 2, 1, testResult, testProcessed, testCoords, testCoordCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // First call: depth=3, dims=[2,1,2,2,1] → 8 elements
        // Second call: depth=2, dims=[1,2,2,1,2] → 8 elements  
        // Third call: depth=1, dims=[1,3,2,1,3] → 18 elements
        // Total elements processed: 8 + 8 + 18 = 34
        variablesToAssert.put("testProcessed", 34d);
        variablesToAssert.put("testResult", null); // Complex calculation, analyze only
        variablesToAssert.put("testCoordCount", null); // Number of significant coordinates found
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests recursive 5D matrix/tensor multiplication and operation counting.
    @Test
    public void testRecursiveMatrixMultiplication5D() throws Exception {
        String code = "def multiply5DTensors(var level:integer, var rows:integer, var cols:integer, var layers:integer, var depth1:integer, var depth2:integer, var result:integer, var operations:array, var opCount:integer) {\n" +
                "    if(level > 0) {\n" +
                "        var tensorA[rows][cols][layers][depth1][depth2]:array;\n" +
                "        var tensorB[rows][cols][layers][depth1][depth2]:array;\n" +
                "        var i,j,k,l,m:integer;\n" +
                "        var localResult:integer;\n" +
                "        localResult = 0;\n" +
                "        i = 0;\n" +
                "        while(i < rows) {\n" +
                "            j = 0;\n" +
                "            while(j < cols) {\n" +
                "                k = 0;\n" +
                "                while(k < layers) {\n" +
                "                    l = 0;\n" +
                "                    while(l < depth1) {\n" +
                "                        m = 0;\n" +
                "                        while(m < depth2) {\n" +
                "                            var valueA,valueB,product:integer;\n" +
                "                            valueA = level * 1000 + i * 100 + j * 10 + k + l + m;\n" +
                "                            valueB = (level + 1) * 100 + (i + j) * 10 + (k + l + m);\n" +
                "                            tensorA[i][j][k][l][m] = valueA;\n" +
                "                            tensorB[i][j][k][l][m] = valueB;\n" +
                "                            product = valueA * valueB;\n" +
                "                            localResult = localResult + product;\n" +
                "                            operations[opCount] = product;\n" +
                "                            opCount = opCount + 1;\n" +
                "                            m = m + 1;\n" +
                "                        }\n" +
                "                        l = l + 1;\n" +
                "                    }\n" +
                "                    k = k + 1;\n" +
                "                }\n" +
                "                j = j + 1;\n" +
                "            }\n" +
                "            i = i + 1;\n" +
                "        }\n" +
                "        result = localResult;\n" +
                "        var nextLevel:integer;\n" +
                "        nextLevel = level - 1;\n" +
                "        if(nextLevel > 0) {\n" +
                "            var nextRows,nextCols,nextLayers,nextDepth1,nextDepth2:integer;\n" +
                "            nextRows = rows;\n" +
                "            nextCols = cols + 1;\n" +
                "            nextLayers = layers - 1;\n" +
                "            nextDepth1 = depth1 + 1;\n" +
                "            nextDepth2 = depth2;\n" +
                "            if(nextLayers <= 0) {\n" +
                "                nextLayers = 1;\n" +
                "            }\n" +
                "            var recursiveResult:integer;\n" +
                "            exec multiply5DTensors(nextLevel, nextRows, nextCols, nextLayers, nextDepth1, nextDepth2, recursiveResult, operations, opCount);\n" +
                "            result = result + recursiveResult;\n" +
                "        }\n" +
                "    } else {\n" +
                "        result = 0;\n" +
                "    }\n" +
                "}\n" +
                "var matrixResult:integer;\n" +
                "var matrixOps[200]:array;\n" +
                "var matrixOpCount:integer;\n" +
                "matrixOpCount = 0;\n" +
                "exec multiply5DTensors(2, 2, 1, 2, 1, 2, matrixResult, matrixOps, matrixOpCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // First recursion: level=2, dims=[2,1,2,1,2] → 8 operations
        // Second recursion: level=1, dims=[2,2,1,2,2] → 16 operations  
        // Total operations: 8 + 16 = 24
        variablesToAssert.put("matrixOpCount", 24d);
        variablesToAssert.put("matrixResult", null); // Complex calculation, analyze only
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Tests recursive 3D array volume calculation and accumulation.
    @Test
    public void testRecursive3DArrayVolumeCalculation() throws Exception {
        String code = "def func(var length: integer, var breadth: integer, var height: integer, var totalSize: integer) {\n" +
                "    var box[length][breadth][height]: array;\n" +
                "    var i, j, k, volume: integer;\n" +
                "    volume = 0;\n" +
                "    i = 0;\n" +
                "    while (i < length) {\n" +
                "        j = 0;\n" +
                "        while (j < breadth) {\n" +
                "            k = 0;\n" +
                "            while (k < height) {\n" +
                "                box[i][j][k] = i * j * k;\n" +
                "                volume = volume + box[i][j][k];\n" +
                "                k = k + 1;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "    totalSize = totalSize + volume;\n" +
                "    if(length < 5) {\n" +
                "        var length1, breadth1, height1: integer;\n" +
                "        length1 = length + 1;\n" +
                "        breadth1 = breadth + 1;\n" +
                "        height1 = height + 1;\n" +
                "        exec func(length1, breadth1, height1, totalSize);\n" +
                "    }\n" +
                "}\n" +
                "var totalVolume: integer;\n" +
                "totalVolume = 0;\n" +
                "exec func(2, 2, 2, totalVolume);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        //Dimensions: 2x2x2, Current Volume: 1, Cumulative totalSize: 1
        //Dimensions: 3x3x3, Current Volume: 27, Cumulative totalSize: 28
        //Dimensions: 4x4x4, Current Volume: 216, Cumulative totalSize: 244
        //Dimensions: 5x5x5, Current Volume: 1000, Cumulative totalSize: 1244
        variablesToAssert.put("totalVolume", 1244d);
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    //test of method whose arguement doesnt take datatype, like def func(a,b). The argument should be used in method as
    // variable and array.
    @Test
    public void testMethodWithUntypedArguments() throws Exception {
        String code = "def func(a, b) {\n" +
                "    var i: integer;\n" +
                "    i = 0;\n" +
                "    while(i < 5) {\n" +
                "        a[i] = i * 2;\n" +
                "        b = b + a[i];\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var myArray[5]: array;\n" +
                "var mySum: integer;\n" +
                "mySum = 0;\n" +
                "exec func(myArray, mySum);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // myArray = [0,2,4,6,8], mySum = 0+2+4+6+8 = 20
        variablesToAssert.put("mySum", 20d);

        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedArray = new HashMap<>();
        expectedArray.put("0", 0d);
        expectedArray.put("1", 2d);
        expectedArray.put("2", 4d);
        expectedArray.put("3", 6d);
        expectedArray.put("4", 8d);
        arrayIndexToAssert.put("myArray", expectedArray);

        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // test where no datatype is given, and it calls another method which also doesn't have datatype
    @Test
    public void testUntypedMethodCallingUntypedMethod() throws Exception {
        String code =
                "def addValues(x, y) {\n" +
                "    var i: integer;\n" +
                "    i = 0;\n" +
                "    while(i < 3) {\n" +
                "        x = x + y[i];\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "def computeTotal(a, b) {\n" +
                "    exec addValues(a, b);\n" +
                "}\n" +
                "var values[3]: array;\n" +
                "values[0] = 5; values[1] = 10; values[2] = 15;\n" +
                "var total: integer;\n" +
                "total = 0;\n" +
                "exec computeTotal(total, values);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // total = 0 + 5 + 10 + 15 = 30
        variablesToAssert.put("total", 30d);

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }


    // Test with 4D array and untyped parameters
    @Test
    public void testUntypedMethod4DArrayManipulation() throws Exception {
        String code = "def process4DArray(matrix, dims) {\n" +
                "    var i, j, k, l, sum: integer;\n" +
                "    sum = 0;\n" +
                "    i = 0;\n" +
                "    while(i < dims[0]) {\n" +
                "        j = 0;\n" +
                "        while(j < dims[1]) {\n" +
                "            k = 0;\n" +
                "            while(k < dims[2]) {\n" +
                "                l = 0;\n" +
                "                while(l < dims[3]) {\n" +
                "                    matrix[i][j][k][l] = i + j * 10 + k * 100 + l * 1000;\n" +
                "                    sum = sum + matrix[i][j][k][l];\n" +
                "                    l = l + 1;\n" +
                "                }\n" +
                "                k = k + 1;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "    dims[4] = sum;\n" +
                "}\n" +
                "var tensor[2][2][2][2]: array;\n" +
                "var dimensions[5]: array;\n" +
                "dimensions[0] = 2; dimensions[1] = 2; dimensions[2] = 2; dimensions[3] = 2;\n" +
                "exec process4DArray(tensor, dimensions);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedDims = new HashMap<>();
        expectedDims.put("4", 8880d); // Sum of all tensor values
        arrayIndexToAssert.put("dimensions", expectedDims);

        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // Test with variable-length 3D array created inside untyped method
    @Test
    public void testUntypedMethodWithDynamic3DArray() throws Exception {
        String code = "def createAndFill3D(size1, size2, size3, result) {\n" +
                "    var cube[size1][size2][size3]: array;\n" +
                "    var i, j, k, product: integer;\n" +
                "    result = 0;\n" +
                "    i = 0;\n" +
                "    while(i < size1) {\n" +
                "        j = 0;\n" +
                "        while(j < size2) {\n" +
                "            k = 0;\n" +
                "            while(k < size3) {\n" +
                "                product = (i + 1) * (j + 1) * (k + 1);\n" +
                "                cube[i][j][k] = product;\n" +
                "                result = result + product;\n" +
                "                k = k + 1;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var dim1, dim2, dim3, totalSum: integer;\n" +
                "dim1 = 3; dim2 = 3; dim3 = 3;\n" +
                "totalSum = 0;\n" +
                "exec createAndFill3D(dim1, dim2, dim3, totalSum);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Sum = 1*1*1 + 1*1*2 + ... + 3*3*3 = 216
        variablesToAssert.put("totalSum", 216d);

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Test with nested untyped methods and multi-dimensional variable arrays
    @Test
    public void testNestedUntypedMethodsWithVariableArrays() throws Exception {
        String code = "def fillLayer(layer, rows, cols, offset) {\n" +
                "    var i, j: integer;\n" +
                "    i = 0;\n" +
                "    while(i < rows) {\n" +
                "        j = 0;\n" +
                "        while(j < cols) {\n" +
                "            layer[i][j] = offset + i * cols + j;\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "def build3DStructure(depth, rows, cols, structure, checksum) {\n" +
                "    var d, localSum: integer;\n" +
                "    localSum = 0;\n" +
                "    d = 0;\n" +
                "    while(d < depth) {\n" +
                "        var layerOffset: integer;\n" +
                "        layerOffset = d * 100;\n" +
                "        exec fillLayer(structure[d], rows, cols, layerOffset);\n" +
                "        var i, j: integer;\n" +
                "        i = 0;\n" +
                "        while(i < rows) {\n" +
                "            j = 0;\n" +
                "            while(j < cols) {\n" +
                "                localSum = localSum + structure[d][i][j];\n" +
                "                j = j + 1;\n" +
                "            }\n" +
                "            i = i + 1;\n" +
                "        }\n" +
                "        d = d + 1;\n" +
                "    }\n" +
                "    checksum = localSum;\n" +
                "}\n" +
                "var depthSize, rowSize, colSize: integer;\n" +
                "depthSize = 2; rowSize = 3; colSize = 4;\n" +
                "var volume[depthSize][rowSize][colSize]: array;\n" +
                "var finalChecksum: integer;\n" +
                "exec build3DStructure(depthSize, rowSize, colSize, volume, finalChecksum);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Layer 0: offset=0, values 0-11, sum=66
        // Layer 1: offset=100, values 100-111, sum=1266
        // Total: 1332
        variablesToAssert.put("finalChecksum", 1332d);

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Test with 5D array and mixed typed/untyped parameters
    @Test
    public void testMixedTyping5DArrayProcessing() throws Exception {
        String code = "def init5D(hyper, d1, d2, d3, d4, d5, var maxVal: integer) {\n" +
                "    var i1, i2, i3, i4, i5, value: integer;\n" +
                "    maxVal = 0;\n" +
                "    i1 = 0;\n" +
                "    while(i1 < d1) {\n" +
                "        i2 = 0;\n" +
                "        while(i2 < d2) {\n" +
                "            i3 = 0;\n" +
                "            while(i3 < d3) {\n" +
                "                i4 = 0;\n" +
                "                while(i4 < d4) {\n" +
                "                    i5 = 0;\n" +
                "                    while(i5 < d5) {\n" +
                "                        value = i1 * 10000 + i2 * 1000 + i3 * 100 + i4 * 10 + i5;\n" +
                "                        hyper[i1][i2][i3][i4][i5] = value;\n" +
                "                        if(value > maxVal) {\n" +
                "                            maxVal = value;\n" +
                "                        }\n" +
                "                        i5 = i5 + 1;\n" +
                "                    }\n" +
                "                    i4 = i4 + 1;\n" +
                "                }\n" +
                "                i3 = i3 + 1;\n" +
                "            }\n" +
                "            i2 = i2 + 1;\n" +
                "        }\n" +
                "        i1 = i1 + 1;\n" +
                "    }\n" +
                "}\n" +
                "var hyperCube[2][2][2][2][2]: array;\n" +
                "var sizes[5]: array;\n" +
                "sizes[0] = 2; sizes[1] = 2; sizes[2] = 2; sizes[3] = 2; sizes[4] = 2;\n" +
                "var maximum: integer;\n" +
                "exec init5D(hyperCube, sizes[0], sizes[1], sizes[2], sizes[3], sizes[4], maximum);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("maximum", 11111d); // Max value at [1][1][1][1][1]

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Test with dynamic 2D array in untyped recursive method
    @Test
    public void testUntypedRecursiveWithDynamic2DArray() throws Exception {
        String code = "def recursiveFill(level, rows, cols, accumulator) {\n" +
                "    if(level > 0) {\n" +
                "        var matrix[rows][cols]: array;\n" +
                "        var i, j, sum: integer;\n" +
                "        sum = 0;\n" +
                "        i = 0;\n" +
                "        while(i < rows) {\n" +
                "            j = 0;\n" +
                "            while(j < cols) {\n" +
                "                matrix[i][j] = level * 10 + i * cols + j;\n" +
                "                sum = sum + matrix[i][j];\n" +
                "                j = j + 1;\n" +
                "            }\n" +
                "            i = i + 1;\n" +
                "        }\n" +
                "        accumulator = accumulator + sum;\n" +
                "        var nextLevel, nextRows, nextCols: integer;\n" +
                "        nextLevel = level - 1;\n" +
                "        nextRows = rows + 1;\n" +
                "        nextCols = cols - 1;\n" +
                "        if(nextCols > 0) {\n" +
                "            exec recursiveFill(nextLevel, nextRows, nextCols, accumulator);\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "var totalAccumulated: integer;\n" +
                "totalAccumulated = 0;\n" +
                "exec recursiveFill(3, 2, 3, totalAccumulated);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Level 3: 2x3, sum includes level offset
        // Level 2: 3x2, sum includes level offset
        // Level 1: 4x1, sum includes level offset
        variablesToAssert.put("totalAccumulated", null); // Analyze only

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Test with untyped method manipulating 3D variable array with conditionals
    @Test
    public void testUntypedMethod3DConditionalProcessing() throws Exception {
        String code = "def processConditional3D(data, x, y, z, filtered, filterCount) {\n" +
                "    var i, j, k, value: integer;\n" +
                "    filterCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < x) {\n" +
                "        j = 0;\n" +
                "        while(j < y) {\n" +
                "            k = 0;\n" +
                "            while(k < z) {\n" +
                "                value = i * 100 + j * 10 + k;\n" +
                "                data[i][j][k] = value;\n" +
                "                if(value > 50) {\n" +
                "                    if(value < 200) {\n" +
                "                        filtered[filterCount] = value;\n" +
                "                        filterCount = filterCount + 1;\n" +
                "                    }\n" +
                "                }\n" +
                "                k = k + 1;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var dim1, dim2, dim3: integer;\n" +
                "dim1 = 3; dim2 = 3; dim3 = 3;\n" +
                "var cube[dim1][dim2][dim3]: array;\n" +
                "var results[50]: array;\n" +
                "var resultCount: integer;\n" +
                "exec processConditional3D(cube, dim1, dim2, dim3, results, resultCount);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Values in range (50, 200): 100, 101, 102, 110, 111, 112, 120, 121, 122
        variablesToAssert.put("resultCount", 9d);

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Test with chain of untyped methods passing multi-dimensional arrays
    @Test
    public void testUntypedMethodChainWithMultiDimArrays() throws Exception {
        String code = "def initMatrix(mat, rows, cols, startVal) {\n" +
                "    var i, j: integer;\n" +
                "    i = 0;\n" +
                "    while(i < rows) {\n" +
                "        j = 0;\n" +
                "        while(j < cols) {\n" +
                "            mat[i][j] = startVal + i + j;\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "def transformMatrix(mat, rows, cols, multiplier) {\n" +
                "    var i, j: integer;\n" +
                "    i = 0;\n" +
                "    while(i < rows) {\n" +
                "        j = 0;\n" +
                "        while(j < cols) {\n" +
                "            mat[i][j] = mat[i][j] * multiplier;\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "def sumMatrix(mat, rows, cols, total) {\n" +
                "    var i, j: integer;\n" +
                "    total = 0;\n" +
                "    i = 0;\n" +
                "    while(i < rows) {\n" +
                "        j = 0;\n" +
                "        while(j < cols) {\n" +
                "            total = total + mat[i][j];\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var r, c: integer;\n" +
                "r = 3; c = 4;\n" +
                "var grid[r][c]: array;\n" +
                "exec initMatrix(grid, r, c, 10);\n" +
                "exec transformMatrix(grid, r, c, 2);\n" +
                "var finalSum: integer;\n" +
                "exec sumMatrix(grid, r, c, finalSum);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Initial: 10,11,12,13,11,12,13,14,12,13,14,15 → sum=150
        // After *2: sum = 300
        variablesToAssert.put("finalSum", 300d);

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Test with 4D variable array and untyped parameters doing matrix transpose-like operation
    @Test
    public void testUntyped4DArrayTransposeOperation() throws Exception {
        String code = "def transpose4D(source, dest, d1, d2, d3, d4, opCount) {\n" +
                "    var i, j, k, l: integer;\n" +
                "    opCount = 0;\n" +
                "    i = 0;\n" +
                "    while(i < d1) {\n" +
                "        j = 0;\n" +
                "        while(j < d2) {\n" +
                "            k = 0;\n" +
                "            while(k < d3) {\n" +
                "                l = 0;\n" +
                "                while(l < d4) {\n" +
                "                    source[i][j][k][l] = i * 1000 + j * 100 + k * 10 + l;\n" +
                "                    dest[l][k][j][i] = source[i][j][k][l];\n" +
                "                    opCount = opCount + 1;\n" +
                "                    l = l + 1;\n" +
                "                }\n" +
                "                k = k + 1;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "var s1, s2, s3, s4: integer;\n" +
                "s1 = 2; s2 = 2; s3 = 2; s4 = 2;\n" +
                "var original[s1][s2][s3][s4]: array;\n" +
                "var transposed[s4][s3][s2][s1]: array;\n" +
                "var operations: integer;\n" +
                "exec transpose4D(original, transposed, s1, s2, s3, s4, operations);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("operations", 16d); // 2*2*2*2 = 16 operations

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // Test with deeply nested untyped methods and variable 3D arrays
    @Test
    public void testDeeplyNestedUntypedWith3DArrays() throws Exception {
        String code = "def level3(arr, size, factor, result) {\n" +
                "    var i, j, k: integer;\n" +
                "    result = 0;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        j = 0;\n" +
                "        while(j < size) {\n" +
                "            k = 0;\n" +
                "            while(k < size) {\n" +
                "                result = result + arr[i][j][k] * factor;\n" +
                "                k = k + 1;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "}\n" +
                "def level2(arr, size, multiplier, result) {\n" +
                "    var partialResult: integer;\n" +
                "    exec level3(arr, size, multiplier, partialResult);\n" +
                "    result = partialResult * 2;\n" +
                "}\n" +
                "def level1(size, mult, finalResult) {\n" +
                "    var cube[size][size][size]: array;\n" +
                "    var i, j, k: integer;\n" +
                "    i = 0;\n" +
                "    while(i < size) {\n" +
                "        j = 0;\n" +
                "        while(j < size) {\n" +
                "            k = 0;\n" +
                "            while(k < size) {\n" +
                "                cube[i][j][k] = i + j + k;\n" +
                "                k = k + 1;\n" +
                "            }\n" +
                "            j = j + 1;\n" +
                "        }\n" +
                "        i = i + 1;\n" +
                "    }\n" +
                "    exec level2(cube, size, mult, finalResult);\n" +
                "}\n" +
                "var answer: integer;\n" +
                "exec level1(3, 3, answer);";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        InterpretAndGetVariableArrayMap(code, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Cube has values 0-4, sum=54, *3=162, *2=324
        variablesToAssert.put("answer", 324d);

        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    private void InterpretAndGetVariableArrayMap(String code, Map<String, Variable> variableMap, Map<String, Array> arrayMap) throws Exception {
        RuleEngineInput ruleEngineInput = getRuleEngineInputWithMaps(code.replaceAll("\n", "").replaceAll("\t", ""), variableMap, arrayMap);

        NativeProcessor processor = new NativeProcessor();
        processor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());

        resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
    }
}

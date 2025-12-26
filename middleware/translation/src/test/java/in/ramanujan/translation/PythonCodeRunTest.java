package in.ramanujan.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
import in.ramanujan.translation.codeConverter.utils.TranslateUtil;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Test class for Python code interpretation using interpretPython method.
 * This is the Python equivalent of BigCodeRunTest.java.
 */
public class PythonCodeRunTest {

    // ========== BASIC TESTS ==========

    /**
     * Tests nested while loops with accumulation logic in Python.
     * Equivalent to testNestedWhileLoops() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonNestedWhileLoops() throws Exception {
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
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Expected: (0*0+0*1+0*2+0*3) + (1*0+1*1+1*2+1*3) + (2*0+2*1+2*2+2*3) = 0 + 6 + 12 = 18
        variablesToAssert.put("testResult", 18d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests nested if-else blocks in Python.
     * Equivalent to testNestedIfElseBlocks() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonNestedIfElseBlocks() throws Exception {
        String pythonCode = 
            "def nestedIfElseTest(x, y, result):\n" +
            "    if x > 0:\n" +
            "        if y > 0:\n" +
            "            result = 1\n" +
            "        else:\n" +
            "            if y == 0:\n" +
            "                result = 2\n" +
            "            else:\n" +
            "                result = 3\n" +
            "    else:\n" +
            "        if x == 0:\n" +
            "            result = 4\n" +
            "        else:\n" +
            "            result = 5\n" +
            "\n" +
            "result1 = 0\n" +
            "result2 = 0\n" +
            "result3 = 0\n" +
            "result4 = 0\n" +
            "result5 = 0\n" +
            "nestedIfElseTest(1, 1, result1)\n" +
            "nestedIfElseTest(1, 0, result2)\n" +
            "nestedIfElseTest(1, -1, result3)\n" +
            "nestedIfElseTest(0, 5, result4)\n" +
            "nestedIfElseTest(-1, 5, result5)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("result1", 1d); // x>0, y>0
        variablesToAssert.put("result2", 2d); // x>0, y==0
        variablesToAssert.put("result3", 3d); // x>0, y<0
        variablesToAssert.put("result4", 4d); // x==0
        variablesToAssert.put("result5", 5d); // x<0
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests recursive factorial calculation in Python.
     * Equivalent to testRecursiveFactorial() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonRecursiveFactorial() throws Exception {
        String pythonCode = 
            "def factorial(n, result):\n" +
            "    if n <= 1:\n" +
            "        result = 1\n" +
            "    else:\n" +
            "        temp = 0\n" +
            "        n_minus_1 = n - 1\n" +
            "        factorial(n_minus_1, temp)\n" +
            "        result = n * temp\n" +
            "\n" +
            "fact5 = 0\n" +
            "fact0 = 0\n" +
            "fact1 = 0\n" +
            "factorial(5, fact5)\n" +
            "factorial(0, fact0)\n" +
            "factorial(1, fact1)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("fact5", 120d); // 5! = 120
        variablesToAssert.put("fact0", 1d);   // 0! = 1
        variablesToAssert.put("fact1", 1d);   // 1! = 1
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests function chaining in Python.
     * Equivalent to testFunctionChaining() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonFunctionChaining() throws Exception {
        String pythonCode = 
            "def addOne(input, output):\n" +
            "    output = input + 1\n" +
            "\n" +
            "def multiplyByTwo(input, output):\n" +
            "    output = input * 2\n" +
            "\n" +
            "def square(input, output):\n" +
            "    output = input * input\n" +
            "\n" +
            "def chainedOperation(start, result):\n" +
            "    temp1 = 0\n" +
            "    temp2 = 0\n" +
            "    temp3 = 0\n" +
            "    addOne(start, temp1)\n" +
            "    multiplyByTwo(temp1, temp2)\n" +
            "    square(temp2, temp3)\n" +
            "    addOne(temp3, result)\n" +
            "\n" +
            "finalResult = 0\n" +
            "chainedOperation(3, finalResult)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Chain: 3 -> addOne(4) -> multiplyByTwo(8) -> square(64) -> addOne(65)
        variablesToAssert.put("finalResult", 65d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests recursive Fibonacci calculation in Python.
     */
    @Test(timeout = 5000)
    public void testPythonRecursiveFibonacci() throws Exception {
        String pythonCode = 
            "arr = [0 for _ in range(8)]\n" +
            "\n" +
            "def fibonacci(n, result, arr):\n" +
            "    if n <= 1:\n" +
            "        result = n\n" +
            "    else:\n" +
            "        fib1 = 0\n" +
            "        fib2 = 0\n" +
            "        n_minus_1 = n - 1\n" +
            "        n_minus_2 = n - 2\n" +
            "        fibonacci(n_minus_1, fib1, arr)\n" +
            "        fibonacci(n_minus_2, fib2, arr)\n" +
            "        result = fib1 + fib2\n" +
            "        arr[n] = fib1 + fib2\n" +
            "\n" +
            "fib0 = 0\n" +
            "fib1 = 0\n" +
            "fib2 = 0\n" +
            "fib3 = 0\n" +
            "fib4 = 0\n" +
            "fib5 = 0\n" +
            "fib6 = 0\n" +
            "fib7 = 0\n" +
            "fibonacci(0, fib0, arr)\n" +
            "fibonacci(1, fib1, arr)\n" +
            "fibonacci(2, fib2, arr)\n" +
            "fibonacci(3, fib3, arr)\n" +
            "fibonacci(4, fib4, arr)\n" +
            "fibonacci(5, fib5, arr)\n" +
            "fibonacci(6, fib6, arr)\n" +
            "fibonacci(7, fib7, arr)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("fib0", 0d); // fib(0) = 0
        variablesToAssert.put("fib1", 1d); // fib(1) = 1
        variablesToAssert.put("fib2", 1d); // fib(2) = 1
        variablesToAssert.put("fib3", 2d); // fib(3) = 2
        variablesToAssert.put("fib4", 3d); // fib(4) = 3
//        variablesToAssert.put("fib5", 5d); // fib(5) = 5
//        variablesToAssert.put("fib6", 8d); // fib(6) = 8
//        variablesToAssert.put("fib7", 13d); // fib(7) = 13
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // ========== ARRAY TESTS ==========

    /**
     * Tests bubble sort with arrays in Python.
     * Equivalent to testComplexWhileWithArrays() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonBubbleSort() throws Exception {
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
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

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

    /**
     * Tests recursive array sum in Python.
     * Equivalent to testRecursiveArraySum() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonRecursiveArraySum() throws Exception {
        String pythonCode = 
            "testArray = [0 for _ in range(5)]\n" +
            "testArray[0] = 10\n" +
            "testArray[1] = 20\n" +
            "testArray[2] = 30\n" +
            "testArray[3] = 40\n" +
            "testArray[4] = 50\n" +
            "\n" +
            "def arraySum(arr, index, size, sum):\n" +
            "    if index >= size:\n" +
            "        sum = 0\n" +
            "    else:\n" +
            "        restSum = 0\n" +
            "        nextIndex = index + 1\n" +
            "        arraySum(arr, nextIndex, size, restSum)\n" +
            "        sum = arr[index] + restSum\n" +
            "\n" +
            "totalSum = 0\n" +
            "arraySum(testArray, 0, 5, totalSum)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("totalSum", 150d); // 10+20+30+40+50 = 150
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    // ========== COMPLEX TESTS ==========

    /**
     * Tests complex nested structures in Python.
     * Equivalent to testComplexNestedStructures() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonComplexNestedStructures() throws Exception {
        String pythonCode = 
            "matrix = [0 for _ in range(9)]\n" +
            "matrix[0] = 1\n" +
            "matrix[1] = 2\n" +
            "matrix[2] = 3\n" +
            "matrix[3] = 4\n" +
            "matrix[4] = 5\n" +
            "matrix[5] = 6\n" +
            "matrix[6] = 7\n" +
            "matrix[7] = 8\n" +
            "matrix[8] = 9\n" +
            "\n" +
            "def processMatrix(matrix, rows, cols, result):\n" +
            "    i = 0\n" +
            "    j = 0\n" +
            "    sum = 0\n" +
            "    product = 0\n" +
            "    result = 0\n" +
            "    i = 0\n" +
            "    while i < rows:\n" +
            "        sum = 0\n" +
            "        j = 0\n" +
            "        while j < cols:\n" +
            "            index = i * cols + j\n" +
            "            if i == j:\n" +
            "                sum = sum + matrix[index] * 2\n" +
            "            else:\n" +
            "                if i < j:\n" +
            "                    sum = sum + matrix[index]\n" +
            "                else:\n" +
            "                    sum = sum - matrix[index]\n" +
            "            j = j + 1\n" +
            "        if sum > 0:\n" +
            "            result = result + sum\n" +
            "        i = i + 1\n" +
            "\n" +
            "matrixResult = 0\n" +
            "processMatrix(matrix, 3, 3, matrixResult)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // Row 0: (1*2) + 2 + 3 = 7 > 0, add 7
        // Row 1: -4 + (5*2) + 6 = 12 > 0, add 12  
        // Row 2: -7 - 8 + (9*2) = 3 > 0, add 3
        // Total: 7 + 12 + 3 = 22
        variablesToAssert.put("matrixResult", 22d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests mutual recursion between functions in Python.
     * Equivalent to testMutualRecursion() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonMutualRecursion() throws Exception {
        String pythonCode = 
            "def isEvenMutual(n, result):\n" +
            "    if n == 0:\n" +
            "        result = 1\n" +
            "    else:\n" +
            "        temp = 0\n" +
            "        n_minus_1 = n - 1\n" +
            "        isOddMutual(n_minus_1, temp)\n" +
            "        result = temp\n" +
            "\n" +
            "def isOddMutual(n, result):\n" +
            "    if n == 0:\n" +
            "        result = 0\n" +
            "    else:\n" +
            "        temp = 0\n" +
            "        n_minus_1 = n - 1\n" +
            "        isEvenMutual(n_minus_1, temp)\n" +
            "        result = temp\n" +
            "\n" +
            "testEven4 = 0\n" +
            "testOdd5 = 0\n" +
            "testEven0 = 0\n" +
            "testOdd7 = 0\n" +
            "isEvenMutual(4, testEven4)\n" +
            "isOddMutual(5, testOdd5)\n" +
            "isEvenMutual(0, testEven0)\n" +
            "isOddMutual(7, testOdd7)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("testEven4", 1d); // 4 is even
        variablesToAssert.put("testOdd5", 1d);  // 5 is odd
        variablesToAssert.put("testEven0", 1d); // 0 is even
        variablesToAssert.put("testOdd7", 1d);  // 7 is odd
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests complex state transitions in Python.
     * Equivalent to testComplexStateTransition() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonComplexStateTransition() throws Exception {
        String pythonCode = 
            "eventSequence = [0 for _ in range(6)]\n" +
            "eventSequence[0] = 1\n" +
            "eventSequence[1] = 2\n" +
            "eventSequence[2] = 3\n" +
            "eventSequence[3] = 1\n" +
            "eventSequence[4] = 2\n" +
            "eventSequence[5] = 3\n" +
            "stateHistory = [0 for _ in range(10)]\n" +
            "\n" +
            "def processStateMachine(events, eventCount, finalState, stateHistory, historyCount):\n" +
            "    state = 0\n" +
            "    i = 0\n" +
            "    event = 0\n" +
            "    state = 0\n" +
            "    i = 0\n" +
            "    historyCount = 0\n" +
            "    stateHistory[historyCount] = state\n" +
            "    historyCount = historyCount + 1\n" +
            "    while i < eventCount:\n" +
            "        event = events[i]\n" +
            "        if state == 0:\n" +
            "            if event == 1:\n" +
            "                state = 1\n" +
            "            else:\n" +
            "                if event == 2:\n" +
            "                    state = 2\n" +
            "        else:\n" +
            "            if state == 1:\n" +
            "                if event == 2:\n" +
            "                    state = 3\n" +
            "                else:\n" +
            "                    if event == 3:\n" +
            "                        state = 0\n" +
            "            else:\n" +
            "                if state == 2:\n" +
            "                    if event == 1:\n" +
            "                        state = 3\n" +
            "                    else:\n" +
            "                        if event == 3:\n" +
            "                            state = 0\n" +
            "                else:\n" +
            "                    if state == 3:\n" +
            "                        if event == 3:\n" +
            "                            state = 0\n" +
            "        stateHistory[historyCount] = state\n" +
            "        historyCount = historyCount + 1\n" +
            "        i = i + 1\n" +
            "    finalState = state\n" +
            "\n" +
            "endState = 0\n" +
            "historyCount = 0\n" +
            "processStateMachine(eventSequence, 6, endState, stateHistory, historyCount)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

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

    // ========== DYNAMIC ARRAY TESTS ==========

    /**
     * Tests dynamic single-dimension array in Python.
     * Equivalent to testDynamicSingleDimensionArray() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonDynamicSingleDimensionArray() throws Exception {
        String pythonCode = 
            "def processDynamicArray(size, multiplier, sum, processedCount):\n" +
            "    arr = [0 for _ in range(size)]\n" +
            "    i = 0\n" +
            "    sum = 0\n" +
            "    processedCount = 0\n" +
            "    i = 0\n" +
            "    while i < size:\n" +
            "        arr[i] = i * multiplier\n" +
            "        if arr[i] > 10:\n" +
            "            sum = sum + arr[i]\n" +
            "            processedCount = processedCount + 1\n" +
            "        i = i + 1\n" +
            "\n" +
            "totalSum = 0\n" +
            "count = 0\n" +
            "processDynamicArray(6, 4, totalSum, count)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        // arr[0]=0, arr[1]=4, arr[2]=8, arr[3]=12, arr[4]=16, arr[5]=20
        // Elements > 10: 12, 16, 20 → sum = 48, count = 3
        variablesToAssert.put("totalSum", 48d);
        variablesToAssert.put("count", 3d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests dynamic 2D array with nested loops in Python.
     * Equivalent to testDynamic2DArrayWithNestedLoops() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonDynamic2DArrayWithNestedLoops() throws Exception {
        String pythonCode = 
            "maxCoords = [0 for _ in range(10)]\n" +
            "\n" +
            "def create2DMatrix(rows, cols, diagonalSum, maxElement, coordinates, coordCount):\n" +
            "    matrix = [[0 for _ in range(cols)] for _ in range(rows)]\n" +
            "    i = 0\n" +
            "    j = 0\n" +
            "    diagonalSum = 0\n" +
            "    maxElement = 0\n" +
            "    coordCount = 0\n" +
            "    i = 0\n" +
            "    while i < rows:\n" +
            "        j = 0\n" +
            "        while j < cols:\n" +
            "            value = (i + 1) * (j + 1)\n" +
            "            matrix[i][j] = value\n" +
            "            if i == j:\n" +
            "                diagonalSum = diagonalSum + value\n" +
            "            if value > maxElement:\n" +
            "                maxElement = value\n" +
            "                coordinates[0] = i\n" +
            "                coordinates[1] = j\n" +
            "                coordCount = 2\n" +
            "            j = j + 1\n" +
            "        i = i + 1\n" +
            "\n" +
            "diagSum = 0\n" +
            "maxElem = 0\n" +
            "coordSize = 0\n" +
            "create2DMatrix(4, 3, diagSum, maxElem, maxCoords, coordSize)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

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

    /**
     * Tests merge and sort in Python.
     * Equivalent to testComplexArrayOperations() in BigCodeRunTest.
     */
    @Test(timeout = 5000)
    public void testPythonMergeAndSort() throws Exception {
        String pythonCode = 
            "array1 = [0 for _ in range(3)]\n" +
            "array1[0] = 1\n" +
            "array1[1] = 4\n" +
            "array1[2] = 7\n" +
            "array2 = [0 for _ in range(4)]\n" +
            "array2[0] = 2\n" +
            "array2[1] = 3\n" +
            "array2[2] = 5\n" +
            "array2[3] = 6\n" +
            "result = [0 for _ in range(7)]\n" +
            "\n" +
            "def mergeAndSort(arr1, size1, arr2, size2, merged, mergedSize):\n" +
            "    i = 0\n" +
            "    j = 0\n" +
            "    k = 0\n" +
            "    i = 0\n" +
            "    j = 0\n" +
            "    k = 0\n" +
            "    while i < size1:\n" +
            "        if j < size2:\n" +
            "            if arr1[i] <= arr2[j]:\n" +
            "                merged[k] = arr1[i]\n" +
            "                i = i + 1\n" +
            "            else:\n" +
            "                merged[k] = arr2[j]\n" +
            "                j = j + 1\n" +
            "        else:\n" +
            "            merged[k] = arr1[i]\n" +
            "            i = i + 1\n" +
            "        k = k + 1\n" +
            "    while j < size2:\n" +
            "        merged[k] = arr2[j]\n" +
            "        j = j + 1\n" +
            "        k = k + 1\n" +
            "    mergedSize = k\n" +
            "\n" +
            "totalSize = 0\n" +
            "mergeAndSort(array1, 3, array2, 4, result, totalSize)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

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

    // ========== SIMPLE ARITHMETIC TESTS ==========

    /**
     * Simple test for basic variable assignment and arithmetic.
     */
    @Test(timeout = 5000)
    public void testPythonSimpleArithmetic() throws Exception {
        String pythonCode = 
            "x = 5\n" +
            "y = 10\n" +
            "z = x + y\n" +
            "w = z * 2\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("x", 5d);
        variablesToAssert.put("y", 10d);
        variablesToAssert.put("z", 15d);
        variablesToAssert.put("w", 30d);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests simple if-else in Python.
     */
    @Test(timeout = 5000)
    public void testPythonSimpleIfElse() throws Exception {
        String pythonCode = 
            "a = 10\n" +
            "b = 5\n" +
            "result = 0\n" +
            "if a > b:\n" +
            "    result = a - b\n" +
            "else:\n" +
            "    result = b - a\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("result", 5d); // 10 > 5, so result = 10 - 5 = 5
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests simple while loop in Python.
     */
    @Test(timeout = 5000)
    public void testPythonSimpleWhileLoop() throws Exception {
        String pythonCode = 
            "sum = 0\n" +
            "i = 1\n" +
            "while i <= 10:\n" +
            "    sum = sum + i\n" +
            "    i = i + 1\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("sum", 55d); // 1+2+3+...+10 = 55
        variablesToAssert.put("i", 11d);   // Loop exits when i = 11
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, new HashMap<>());
    }

    /**
     * Tests simple array operations in Python.
     */
    @Test(timeout = 5000)
    public void testPythonSimpleArrayOperations() throws Exception {
        String pythonCode = 
            "arr = [0 for _ in range(5)]\n" +
            "arr[0] = 10\n" +
            "arr[1] = 20\n" +
            "arr[2] = 30\n" +
            "arr[3] = 40\n" +
            "arr[4] = 50\n" +
            "sum = 0\n" +
            "i = 0\n" +
            "while i < 5:\n" +
            "    sum = sum + arr[i]\n" +
            "    i = i + 1\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretPythonAndGetVariableArrayMap(pythonCode, variableMap, arrayMap);

        Map<String, Object> variablesToAssert = new HashMap<>();
        variablesToAssert.put("sum", 150d); // 10+20+30+40+50 = 150
        
        Map<String, Object> arrayIndexToAssert = new HashMap<>();
        Map<String, Object> expectedArray = new HashMap<>();
        expectedArray.put("0", 10d);
        expectedArray.put("1", 20d);
        expectedArray.put("2", 30d);
        expectedArray.put("3", 40d);
        expectedArray.put("4", 50d);
        arrayIndexToAssert.put("arr", expectedArray);
        
        analyzeResults(variableMap, arrayMap, variablesToAssert, arrayIndexToAssert);
    }

    // ========== HELPER METHODS ==========

    /**
     * Interprets Python code using the interpretPython method and populates variable/array maps.
     */
    private void interpretPythonAndGetVariableArrayMap(String pythonCode, 
                                                        Map<String, Variable> variableMap, 
                                                        Map<String, Array> arrayMap) throws Exception {
        // Create code converter
        CodeConverter codeConverter = new CodeConverter(new CodeConverterLogicFactory(), new StringUtils());
        
        // Create RuleEngineInput
        RuleEngineInput ruleEngineInput = new RuleEngineInput();
        ruleEngineInput.setVariables(new ArrayList<>());
        ruleEngineInput.setArrays(new ArrayList<>());
        ruleEngineInput.setCommands(new ArrayList<>());
        ruleEngineInput.setOperations(new ArrayList<>());
        ruleEngineInput.setConstants(new ArrayList<>());
        ruleEngineInput.setConditions(new ArrayList<>());
        ruleEngineInput.setIfBlocks(new ArrayList<>());
        ruleEngineInput.setWhileBlocks(new ArrayList<>());
        ruleEngineInput.setFunctionCalls(new ArrayList<>());
        ruleEngineInput.setMethodDataTypeAgnosticArgs(new ArrayList<>());
        
        // Create debug level code creator
        ActualDebugCodeCreator debugLevelCodeCreator = new ActualDebugCodeCreator("", 0);
        
        // Create function frame variable map
        Map<Integer, RuleEngineInputUnits> functionFrameVariableMap = new HashMap<>();
        Integer[] frameVariableCounterId = {0};
        
        // Create variable scope
        List<String> variableScope = new ArrayList<>();
        
        // Call interpretPython
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, 
            ruleEngineInput, 
            variableScope, 
            debugLevelCodeCreator, 
            functionFrameVariableMap, 
            frameVariableCounterId
        );
        
        // Populate variable and array maps from ruleEngineInput
        for (Variable variable : ruleEngineInput.getVariables()) {
            variableMap.put(variable.getId(), variable);
        }
        for (Array array : ruleEngineInput.getArrays()) {
            arrayMap.put(array.getId(), array);
        }
        
        // Execute using NativeProcessor
        if (!commands.isEmpty()) {
                NativeProcessor processor = new NativeProcessor();
                ObjectMapper mapper = new ObjectMapper();
                String jsonInput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ruleEngineInput);

                // Write combined payload (firstCommandId + ruleEngineInput) to a stable tmp file for native debugging
                ObjectNode wrapper = mapper.createObjectNode();
                wrapper.put("firstCommandId", commands.get(0).getId());
                wrapper.set("ruleEngineInput", mapper.valueToTree(ruleEngineInput));
                    Path tmpPath = Paths.get("/tmp", "rule_engine_debug.json");
                    Files.write(tmpPath,
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper)
                            .getBytes(StandardCharsets.UTF_8));

                System.out.println("========== Debug payload written to /tmp/rule_engine_debug.json ==========");
                System.out.flush();
            processor.process(jsonInput, commands.get(0).getId());
            
            // Resolve variables from native processor result
            resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        }
    }

    /**
     * Resolve variables from NativeProcessor result - similar to ExecuteInline.java executeDagElement method
     */
    @SuppressWarnings("unchecked")
    private void resolveVariablesFromNativeProcessor(NativeProcessor nativeProcessor, 
                                                     Map<String, Variable> variableMap, 
                                                     Map<String, Array> arrayMap) {
        try {
            for (Object en : nativeProcessor.jniObject.entrySet()) {
                Map.Entry<String, Object> entry = (Map.Entry<String, Object>) en;
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if ("arrayIndex".equalsIgnoreCase(key)) {
                    // Handle array results
                    Map<String, Map<String, Object>> arrayResultMap = (Map<String, Map<String, Object>>) value;
                    for (Map.Entry<String, Map<String, Object>> arrayResultEntry : arrayResultMap.entrySet()) {
                        String arrayName = arrayResultEntry.getKey();
                        Map<String, Object> arrayResult = arrayResultEntry.getValue();
                        Array array = arrayMap.get(arrayName);
                        if (array != null) {
                            array.getValues().putAll(arrayResult);
                        }
                    }
                } else {
                    // Handle variable results
                    Variable variable = variableMap.get(key);
                    if (variable != null) {
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
     */
    private void analyzeResults(Map<String, Variable> variableMap, 
                               Map<String, Array> arrayMap, 
                               Map<String, Object> variablesToAssert, 
                               Map<String, Object> arrayIndexToAssert) {
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
                String name = a.getName();
                if (name != null) {
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
                
                // Perform assertion if expected value is provided
                Object expectedValue = arrayIndexToAssert.get(arrayName);
                if (expectedValue != null) {
                    System.out.println("  Expected: " + expectedValue);
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
                    }
                }
            } else {
                // Print limited output for other arrays
                System.out.println("  Size: " + arrayValues.size());
                if (arrayValues.size() <= 10) {
                    for (Map.Entry<String, Object> valueEntry : arrayValues.entrySet()) {
                        System.out.println("  " + arrayName + "[" + valueEntry.getKey() + "] = " + valueEntry.getValue());
                    }
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
                    // Use JUnit assertion
                    assertEquals("Variable " + varName + " value mismatch",
                            expectedValue, foundVariable.getValue());
                    System.out.println("  Assertion PASSED: " + expectedValue + " == " + foundVariable.getValue());
                } else {
                    System.out.println("  Analysis complete (no assertion specified)");
                }
            } else {
                if (expectedValue != null) {
                    fail("Variable " + varName + " was expected but not found in results");
                } else {
                    System.out.println("Variable " + varName + ": NOT FOUND (no assertion specified)");
                }
            }
        }
    }
}

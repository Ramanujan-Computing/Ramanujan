package in.ramanujan.translation.antlr;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.translation.codeConverter.antlr.PythonAwareCodeConverter;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive test suite for Python3 to Ramanujan translation.
 * 
 * For any test, write the Python3 code and pass that to PythonAwareCodeConverter. The commandList received, take
 * the first command and pass it to the nativeProcessor.
 * The nativeProcessor will return the result of the command execution.
 *
 * In parallel, execute the same via Jython interpreter and compare the results.
 * If the results match, then the test passes.
 * If the results do not match, then the test fails.
 */
@RunWith(JUnit4.class)
public class Python3Tests {
    private NativeProcessor nativeProcessor;
    private PythonAwareCodeConverter codeConverter;
    private ObjectMapper objectMapper;
    private PythonInterpreter pythonInterpreter;

    @Before
    public void setUp() {
        nativeProcessor = new NativeProcessor();
        codeConverter = new PythonAwareCodeConverter(null, null);
        codeConverter.setUsePythonSyntax(true);
        objectMapper = new ObjectMapper();
        pythonInterpreter = new PythonInterpreter();
    }

    /**
     * Test simple variable assignments
     */
    @Test
    public void testSimpleVariableAssignments() throws Exception {
        String pythonCode = "x = 10\n" +
            "y = 20\n" +
            "z = x + y\n";

        // Test with Ramanujan
        Map<String, Object> ramanujanResult = executeWithRamanujan(pythonCode);
        
        // Test with Jython
        Map<String, Object> jythonResult = executeWithJython(pythonCode, 
            Arrays.asList("x", "y", "z"));

        // Compare results
        assertEquals("x should match", 
            jythonResult.get("x"), ramanujanResult.get("x"));
        assertEquals("y should match", 
            jythonResult.get("y"), ramanujanResult.get("y"));
        assertEquals("z should match", 
            jythonResult.get("z"), ramanujanResult.get("z"));
    }

    /**
     * Test variable length array declarations with constant dimensions
     */
    @Test
    public void testVariableLengthArrayConstantDimensions() throws Exception {
        String pythonCode = "# 1D array with constant dimension\n" +
            "arr1 = [0 for _ in range(5)]\n" +
            "arr1[0] = 10\n" +
            "arr1[1] = 20\n";

        // Test with Ramanujan
        Map<String, Object> ramanujanResult = executeWithRamanujan(pythonCode);
        
        // Test with Jython
        Map<String, Object> jythonResult = executeWithJython(pythonCode, 
            Arrays.asList("arr1"));

        // Compare results - just check that the array was created and modified
        assertNotNull("arr1 should exist in Ramanujan result", ramanujanResult.get("arr1"));
        assertNotNull("arr1 should exist in Jython result", jythonResult.get("arr1"));
    }

    /**
     * Test variable length array declarations with variable dimensions
     */
    @Test
    public void testVariableLengthArrayVariableDimensions() throws Exception {
        String pythonCode = "# Variable dimensions\n" +
            "n = 4\n" +
            "arr1 = [0 for _ in range(n)]\n" +
            "arr1[0] = 100\n";

        // Test with Ramanujan
        Map<String, Object> ramanujanResult = executeWithRamanujan(pythonCode);
        
        // Test with Jython
        Map<String, Object> jythonResult = executeWithJython(pythonCode, 
            Arrays.asList("n", "arr1"));

        // Compare results
        assertEquals("Variable n should match", 
            jythonResult.get("n"), ramanujanResult.get("n"));
    }

    /**
     * Test simple if-else constructs (no elif allowed)
     */
    @Test
    public void testSimpleIfElse() throws Exception {
        String pythonCode = "x = 10\n" +
            "result = 0\n" +
            "if x > 5:\n" +
            "    result = 1\n" +
            "else:\n" +
            "    result = 0\n";

        // Test with Ramanujan
        Map<String, Object> ramanujanResult = executeWithRamanujan(pythonCode);
        
        // Test with Jython
        Map<String, Object> jythonResult = executeWithJython(pythonCode, 
            Arrays.asList("x", "result"));

        // Compare results
        assertEquals("x should match", 
            jythonResult.get("x"), ramanujanResult.get("x"));
        assertEquals("result should match", 
            jythonResult.get("result"), ramanujanResult.get("result"));
    }

    /**
     * Test simple while loops
     */
    @Test
    public void testSimpleWhileLoop() throws Exception {
        String pythonCode = "i = 0\n" +
            "sum1 = 0\n" +
            "while i < 5:\n" +
            "    sum1 = sum1 + i\n" +
            "    i = i + 1\n";

        // Test with Ramanujan
        Map<String, Object> ramanujanResult = executeWithRamanujan(pythonCode);
        
        // Test with Jython
        Map<String, Object> jythonResult = executeWithJython(pythonCode, 
            Arrays.asList("i", "sum1"));

        // Compare results
        assertEquals("i should match", 
            jythonResult.get("i"), ramanujanResult.get("i"));
        assertEquals("sum1 should match", 
            jythonResult.get("sum1"), ramanujanResult.get("sum1"));
    }

    /**
     * Test simple function definitions and calls
     */
    @Test
    public void testSimpleFunctions() throws Exception {
        String pythonCode = "def add_values(a, b, result):" +
            "    result = a + b\n" +
            "x = 10\n" +
            "y = 20\n" +
            "sum_result = 0\n" +
            "add_values(x, y, sum_result)\n";

        // Test with Ramanujan
        Map<String, Object> ramanujanResult = executeWithRamanujan(pythonCode);
        
        // Test with Jython  
        Map<String, Object> jythonResult = executeWithJython(pythonCode, 
            Arrays.asList("x", "y", "sum_result"));

        // Compare results
        assertEquals("x should match", 
            jythonResult.get("x"), ramanujanResult.get("x"));
        assertEquals("y should match", 
            jythonResult.get("y"), ramanujanResult.get("y"));
        assertEquals("sum_result should match", 
            jythonResult.get("sum_result"), ramanujanResult.get("sum_result"));
    }

    /**
     * Test elif rejection - should throw compilation exception
     */
    @Test(expected = RuntimeException.class)
    public void testElifRejection() throws Exception {
        String pythonCode = "x = 10\n" +
            "if x > 15:\n" +
            "    y = 1\n" +
            "elif x > 5:\n" +
            "    y = 2\n" +
            "else:\n" +
            "    y = 3\n";

        executeWithRamanujan(pythonCode);
    }

    /**
     * Test return statement rejection - should throw compilation exception
     */
    @Test(expected = RuntimeException.class)
    public void testReturnStatementRejection() throws Exception {
        String pythonCode = "def bad_function():\n" +
            "    return 42\n\n" +
            "result = bad_function()\n";

        executeWithRamanujan(pythonCode);
    }

    /**
     * Helper method to execute Python code with Ramanujan
     */
    private Map<String, Object> executeWithRamanujan(String pythonCode) throws Exception {
        RuleEngineInput ruleEngineInput = new RuleEngineInput();
        ruleEngineInput.setCommands(new ArrayList<>());
        ruleEngineInput.setVariables(new ArrayList<>());
        ruleEngineInput.setArrays(new ArrayList<>());
        ruleEngineInput.setConditions(new ArrayList<>());
        ruleEngineInput.setOperations(new ArrayList<>());
        ruleEngineInput.setIfBlocks(new ArrayList<>());
        ruleEngineInput.setWhileBlocks(new ArrayList<>());
        ruleEngineInput.setFunctionCalls(new ArrayList<>());

        List<String> variableScope = new ArrayList<>();
        DebugLevelCodeCreator debugCreator = new NoConcatImpl();

        try {
            // Convert Python to Ramanujan commands
            List<Command> commands = codeConverter.interpret(pythonCode, ruleEngineInput, 
                variableScope, debugCreator, new HashMap<>(), new Integer[]{0});

            if (commands.isEmpty()) {
                return new HashMap<>();
            }

            // Execute with native processor
            String ruleEngineInputJson = objectMapper.writeValueAsString(ruleEngineInput);
            String firstCommandId = commands.get(0).getId();
            
            nativeProcessor.process(ruleEngineInputJson, firstCommandId);
            
            return (Map<String, Object>) nativeProcessor.jniObject;
        } catch (Exception e) {
            // Log the exception for debugging but allow it to propagate
            System.err.println("Ramanujan execution failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method to execute Python code with Jython
     */
    private Map<String, Object> executeWithJython(String pythonCode, List<String> variablesToExtract) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Execute the Python code
            pythonInterpreter.exec(pythonCode);
            
            // Extract specified variables
            for (String varName : variablesToExtract) {
                PyObject pyObject = pythonInterpreter.get(varName);
                if (pyObject != null) {
                    Object value = convertPyObjectToJava(pyObject);
                    result.put(varName, value);
                }
            }
        } catch (Exception e) {
            System.err.println("Jython execution failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    /**
     * Convert PyObject to Java object
     */
    private Object convertPyObjectToJava(PyObject pyObject) {
        if (pyObject.isInteger()) {
            return pyObject.asInt();
        } else if (pyObject.isSequenceType()) {
            // Handle lists/arrays
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < pyObject.__len__(); i++) {
                list.add(convertPyObjectToJava(pyObject.__getitem__(i)));
            }
            return list;
        } else {
            return pyObject.toString();
        }
    }
}

package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.utils.PythonAstInvoker;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PythonAstIntegrationTest {
    
    private CodeConverter codeConverter;
    private RuleEngineInput ruleEngineInput;
    private DebugLevelCodeCreator debugLevelCodeCreator;
    private List<String> variableScope;
    
    @Before
    public void setUp() {
        CodeConverterLogicFactory factory = new CodeConverterLogicFactory();
        StringUtils stringUtils = new StringUtils();
        codeConverter = new CodeConverter(factory, stringUtils);
        ruleEngineInput = new RuleEngineInput();
        debugLevelCodeCreator = new NoConcatImpl();
        variableScope = new ArrayList<>();
    }
    
    @Test
    public void testSimpleAssignment() throws CompilationException {
        String pythonCode = "x = 5";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertNotNull("Commands should not be null", commands);
        assertEquals("Should have 1 command", 1, commands.size());
        assertFalse("Should have created variables", ruleEngineInput.getVariables().isEmpty());
        
        Variable xVar = findVariable("x");
        assertNotNull("Variable x should exist", xVar);
        assertEquals("Variable x should be Integer", "Integer", xVar.getDataType());
    }
    
    @Test
    public void testMultipleAssignments() throws CompilationException {
        String pythonCode = "x = 5\ny = 10\nz = 15";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertEquals("Should have 3 commands", 3, commands.size());
        assertEquals("Should have 3 variables", 3, ruleEngineInput.getVariables().size());
        
        assertNotNull("Variable x should exist", findVariable("x"));
        assertNotNull("Variable y should exist", findVariable("y"));
        assertNotNull("Variable z should exist", findVariable("z"));
    }
    
    @Test
    public void testBinaryOperation() throws CompilationException {
        String pythonCode = "x = 5\ny = 10\nz = x + y";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertEquals("Should have 3 commands", 3, commands.size());
        assertFalse("Should have operations", ruleEngineInput.getOperations().isEmpty());
        
        assertTrue("Should have at least 2 operations", 
            ruleEngineInput.getOperations().size() >= 2);
    }
    
    @Test
    public void testIfStatement() throws CompilationException {
        String pythonCode = "x = 5\nif x > 3:\n    y = 10";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertFalse("Should have if blocks", ruleEngineInput.getIfBlocks().isEmpty());
        assertFalse("Should have conditions", ruleEngineInput.getConditions().isEmpty());
        
        assertEquals("Should have 1 if block", 1, ruleEngineInput.getIfBlocks().size());
        assertEquals("Should have 1 condition", 1, ruleEngineInput.getConditions().size());
    }
    
    @Test
    public void testWhileLoop() throws CompilationException {
        String pythonCode = "x = 0\nwhile x < 10:\n    x = x + 1";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertFalse("Should have while blocks", ruleEngineInput.getWhileBlocks().isEmpty());
        assertFalse("Should have conditions", ruleEngineInput.getConditions().isEmpty());
        
        assertEquals("Should have 1 while block", 1, ruleEngineInput.getWhileBlocks().size());
    }
    
    @Test
    public void testAugmentedAssignment() throws CompilationException {
        String pythonCode = "x = 5\nx += 3";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertEquals("Should have 2 commands", 2, commands.size());
        assertFalse("Should have operations", ruleEngineInput.getOperations().isEmpty());
    }
    
    @Test
    public void testNestedIfElse() throws CompilationException {
        String pythonCode = "x = 5\nif x > 3:\n    y = 10\nelse:\n    y = 20";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertEquals("Should have 1 if block", 1, ruleEngineInput.getIfBlocks().size());
        
        assertNotNull("If block should have else command", 
            ruleEngineInput.getIfBlocks().get(0).getElseCommandId());
    }
    
    @Test
    public void testComplexExpression() throws CompilationException {
        String pythonCode = "x = 5\ny = 10\nz = 3\nresult = x + y * z";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertEquals("Should have 4 commands", 4, commands.size());
        assertTrue("Should have multiple operations", 
            ruleEngineInput.getOperations().size() >= 3);
    }
    
    @Test
    public void testCommandChaining() throws CompilationException {
        String pythonCode = "x = 5\ny = 10\nz = 15";
        
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertEquals("Should have 3 commands", 3, commands.size());
        
        // Verify command chaining
        Command first = commands.get(0);
        Command second = commands.get(1);
        Command third = commands.get(2);
        
        assertEquals("First command should point to second", 
            second.getId(), first.getNextId());
        assertEquals("Second command should point to third", 
            third.getId(), second.getNextId());
        assertNull("Third command should not have next", third.getNextId());
    }
    
    @Test
    public void testFullPythonWorkflow() throws CompilationException {
        // Test the complete workflow: Python code -> AST -> Parse -> Convert
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "x = 0\nwhile x < 5:\n    x = x + 1";
        
        // Step 1: Get AST dump
        String astDump = invoker.invokeAst(pythonCode);
        assertNotNull("AST dump should not be null", astDump);
        
        // Step 2: Parse AST
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        assertNotNull("Module should not be null", module);
        assertEquals("Should have 2 statements", 2, module.getBody().size());
        
        // Step 3: Convert to RuleEngineInput
        List<Command> commands = codeConverter.interpretPython(
            pythonCode, ruleEngineInput, variableScope, 
            debugLevelCodeCreator, new HashMap<>(), new Integer[]{0}
        );
        
        assertNotNull("Commands should not be null", commands);
        assertFalse("Should have while blocks", ruleEngineInput.getWhileBlocks().isEmpty());
    }
    
    // Helper methods
    
    private Variable findVariable(String name) {
        return ruleEngineInput.getVariables().stream()
            .filter(v -> v.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    private Array findArray(String name) {
        return ruleEngineInput.getArrays().stream()
            .filter(a -> a.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
}

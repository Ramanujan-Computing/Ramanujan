package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.utils.PythonAstInvoker;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test the complete JSON-based AST pipeline:
 * Python code -> ast2json -> JsonAstParser -> ModuleNode
 */
public class JsonAstParserTest {

    @Test
    public void testSimpleAssignment() throws CompilationException {
        String pythonCode = "x = 5";
        
        // Step 1: Invoke Python to get AST JSON
        PythonAstInvoker invoker = new PythonAstInvoker();
        String astJson = invoker.invokeAstJson(pythonCode);
        
        System.out.println("AST JSON: " + astJson);
        
        // Step 2: Parse JSON to AST
        JsonAstParser parser = new JsonAstParser();
        ModuleNode module = parser.parseJson(astJson);
        
        // Verify
        assertNotNull("Module should not be null", module);
        assertNotNull("Module body should not be null", module.getBody());
        assertEquals("Module should have 1 statement", 1, module.getBody().size());
        
        AstNode firstStmt = module.getBody().get(0);
        assertTrue("First statement should be AssignNode", firstStmt instanceof AssignNode);
        
        AssignNode assign = (AssignNode) firstStmt;
        assertEquals("Should have 1 target", 1, assign.getTargets().size());
        
        AstNode target = assign.getTargets().get(0);
        assertTrue("Target should be NameNode", target instanceof NameNode);
        assertEquals("Target name should be 'x'", "x", ((NameNode) target).getId());
        
        AstNode value = assign.getValue();
        assertTrue("Value should be ConstantNode", value instanceof ConstantNode);
        assertEquals("Value should be 5", 5, ((ConstantNode) value).getValue());
    }
    
    @Test
    public void testBinaryOperation() throws CompilationException {
        String pythonCode = "result = x + y";
        
        PythonAstInvoker invoker = new PythonAstInvoker();
        String astJson = invoker.invokeAstJson(pythonCode);
        
        JsonAstParser parser = new JsonAstParser();
        ModuleNode module = parser.parseJson(astJson);
        
        assertNotNull(module);
        assertEquals(1, module.getBody().size());
        
        AssignNode assign = (AssignNode) module.getBody().get(0);
        AstNode value = assign.getValue();
        assertTrue("Value should be BinOpNode", value instanceof BinOpNode);
        
        BinOpNode binOp = (BinOpNode) value;
        assertEquals("Operator should be Add", "Add", binOp.getOp());
        assertTrue("Left operand should be NameNode", binOp.getLeft() instanceof NameNode);
        assertTrue("Right operand should be NameNode", binOp.getRight() instanceof NameNode);
    }
    
    @Test
    public void testIfStatement() throws CompilationException {
        String pythonCode = "if x > 5:\n    y = 10";
        
        PythonAstInvoker invoker = new PythonAstInvoker();
        String astJson = invoker.invokeAstJson(pythonCode);
        
        JsonAstParser parser = new JsonAstParser();
        ModuleNode module = parser.parseJson(astJson);
        
        assertNotNull(module);
        assertEquals(1, module.getBody().size());
        
        AstNode firstStmt = module.getBody().get(0);
        assertTrue("First statement should be IfNode", firstStmt instanceof IfNode);
        
        IfNode ifNode = (IfNode) firstStmt;
        assertNotNull("Test condition should not be null", ifNode.getTest());
        assertTrue("Test should be CompareNode", ifNode.getTest() instanceof CompareNode);
        assertNotNull("If body should not be null", ifNode.getBody());
        assertEquals("If body should have 1 statement", 1, ifNode.getBody().size());
    }
    
    @Test
    public void testWhileLoop() throws CompilationException {
        String pythonCode = "while count < 10:\n    count = count + 1";
        
        PythonAstInvoker invoker = new PythonAstInvoker();
        String astJson = invoker.invokeAstJson(pythonCode);
        
        JsonAstParser parser = new JsonAstParser();
        ModuleNode module = parser.parseJson(astJson);
        
        assertNotNull(module);
        assertEquals(1, module.getBody().size());
        
        AstNode firstStmt = module.getBody().get(0);
        assertTrue("First statement should be WhileNode", firstStmt instanceof WhileNode);
        
        WhileNode whileNode = (WhileNode) firstStmt;
        assertNotNull("Test condition should not be null", whileNode.getTest());
        assertNotNull("While body should not be null", whileNode.getBody());
        assertEquals("While body should have 1 statement", 1, whileNode.getBody().size());
    }
    
    @Test
    public void testFunctionDefinition() throws CompilationException {
        String pythonCode = "def add(a, b):\n    return a + b";
        
        PythonAstInvoker invoker = new PythonAstInvoker();
        String astJson = invoker.invokeAstJson(pythonCode);
        
        JsonAstParser parser = new JsonAstParser();
        ModuleNode module = parser.parseJson(astJson);
        
        assertNotNull(module);
        assertEquals(1, module.getBody().size());
        
        AstNode firstStmt = module.getBody().get(0);
        assertTrue("First statement should be FunctionDefNode", firstStmt instanceof FunctionDefNode);
        
        FunctionDefNode funcDef = (FunctionDefNode) firstStmt;
        assertEquals("Function name should be 'add'", "add", funcDef.getName());
        assertNotNull("Function args should not be null", funcDef.getArgs());
        assertNotNull("Function body should not be null", funcDef.getBody());
        assertEquals("Function body should have 1 statement", 1, funcDef.getBody().size());
    }
    
    @Test
    public void testArrayAccess() throws CompilationException {
        String pythonCode = "value = arr[0]";
        
        PythonAstInvoker invoker = new PythonAstInvoker();
        String astJson = invoker.invokeAstJson(pythonCode);
        
        JsonAstParser parser = new JsonAstParser();
        ModuleNode module = parser.parseJson(astJson);
        
        assertNotNull(module);
        assertEquals(1, module.getBody().size());
        
        AssignNode assign = (AssignNode) module.getBody().get(0);
        AstNode value = assign.getValue();
        assertTrue("Value should be SubscriptNode", value instanceof SubscriptNode);
        
        SubscriptNode subscript = (SubscriptNode) value;
        assertTrue("Subscript value should be NameNode", subscript.getValue() instanceof NameNode);
        assertTrue("Subscript slice should be ConstantNode", subscript.getSlice() instanceof ConstantNode);
    }
}

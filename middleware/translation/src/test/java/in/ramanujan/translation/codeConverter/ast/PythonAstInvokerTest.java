package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.utils.PythonAstInvoker;
import org.junit.Test;

import static org.junit.Assert.*;

public class PythonAstInvokerTest {
    
    @Test
    public void testSimpleAssignment() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "x = 5";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain Module", astDump.contains("Module"));
        assertTrue("AST should contain Assign", astDump.contains("Assign"));
        assertTrue("AST should contain Name", astDump.contains("Name"));
        assertTrue("AST should contain Constant", astDump.contains("Constant"));
    }
    
    @Test
    public void testMultipleStatements() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "x = 5\ny = 10\nz = x + y";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain Module", astDump.contains("Module"));
        assertTrue("AST should contain multiple Assign nodes", 
            astDump.indexOf("Assign") != astDump.lastIndexOf("Assign"));
    }
    
    @Test
    public void testIfStatement() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "if x > 5:\n    y = 10";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain If", astDump.contains("If"));
        assertTrue("AST should contain Compare", astDump.contains("Compare"));
        assertTrue("AST should contain Gt", astDump.contains("Gt"));
    }
    
    @Test
    public void testWhileLoop() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "while x < 10:\n    x = x + 1";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain While", astDump.contains("While"));
        assertTrue("AST should contain Compare", astDump.contains("Compare"));
        assertTrue("AST should contain Lt", astDump.contains("Lt"));
    }
    
    @Test
    public void testBinaryOperation() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "result = x + y * z";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain BinOp", astDump.contains("BinOp"));
        assertTrue("AST should contain Add", astDump.contains("Add"));
        assertTrue("AST should contain Mult", astDump.contains("Mult"));
    }
    
    @Test
    public void testFunctionDefinition() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "def add(a, b):\n    return a + b";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain FunctionDef", astDump.contains("FunctionDef"));
        assertTrue("AST should contain arguments", astDump.contains("arguments"));
        assertTrue("AST should contain Return", astDump.contains("Return"));
    }
    
    @Test
    public void testArrayAccess() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "x = arr[0]";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain Subscript", astDump.contains("Subscript"));
    }
    
    @Test
    public void testListCreation() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "arr = [1, 2, 3, 4, 5]";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain List", astDump.contains("List"));
    }
    
    @Test
    public void testFunctionCall() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "result = add(x, y)";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain Call", astDump.contains("Call"));
    }
    
    @Test
    public void testAugmentedAssignment() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "x += 5";
        String astDump = invoker.invokeAst(pythonCode);
        
        assertNotNull("AST dump should not be null", astDump);
        assertTrue("AST should contain AugAssign", astDump.contains("AugAssign"));
        assertTrue("AST should contain Add", astDump.contains("Add"));
    }
    
    @Test(expected = CompilationException.class)
    public void testInvalidPythonCode() throws CompilationException {
        PythonAstInvoker invoker = new PythonAstInvoker();
        String pythonCode = "if x > 5 invalid syntax";
        invoker.invokeAst(pythonCode);
    }
}

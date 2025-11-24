package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.translation.codeConverter.exception.CompilationException;
import org.junit.Test;

import static org.junit.Assert.*;

public class AstParserTest {
    
    @Test
    public void testParseSimpleAssignment() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    Assign(\n" +
                "      targets=[Name(id='x', ctx=Store())],\n" +
                "      value=Constant(value=5))],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 1 statement", 1, module.getBody().size());
        
        AstNode firstNode = module.getBody().get(0);
        assertTrue("First node should be AssignNode", firstNode instanceof AssignNode);
        
        AssignNode assign = (AssignNode) firstNode;
        assertEquals("Should have 1 target", 1, assign.getTargets().size());
        assertTrue("Target should be NameNode", assign.getTargets().get(0) instanceof NameNode);
        
        NameNode target = (NameNode) assign.getTargets().get(0);
        assertEquals("Target name should be 'x'", "x", target.getId());
        
        assertTrue("Value should be ConstantNode", assign.getValue() instanceof ConstantNode);
        ConstantNode value = (ConstantNode) assign.getValue();
        assertEquals("Value should be 5", 5, value.getValue());
    }
    
    @Test
    public void testParseBinaryOperation() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    Assign(\n" +
                "      targets=[Name(id='result', ctx=Store())],\n" +
                "      value=BinOp(\n" +
                "        left=Name(id='x', ctx=Load()),\n" +
                "        op=Add(),\n" +
                "        right=Name(id='y', ctx=Load())))],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        AssignNode assign = (AssignNode) module.getBody().get(0);
        assertTrue("Value should be BinOpNode", assign.getValue() instanceof BinOpNode);
        
        BinOpNode binOp = (BinOpNode) assign.getValue();
        assertEquals("Operator should be Add", "Add", binOp.getOp());
        assertTrue("Left should be NameNode", binOp.getLeft() instanceof NameNode);
        assertTrue("Right should be NameNode", binOp.getRight() instanceof NameNode);
    }
    
    @Test
    public void testParseIfStatement() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    If(\n" +
                "      test=Compare(\n" +
                "        left=Name(id='x', ctx=Load()),\n" +
                "        ops=[Gt()],\n" +
                "        comparators=[Constant(value=5)]),\n" +
                "      body=[\n" +
                "        Assign(\n" +
                "          targets=[Name(id='y', ctx=Store())],\n" +
                "          value=Constant(value=10))],\n" +
                "      orelse=[])],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertTrue("First node should be IfNode", module.getBody().get(0) instanceof IfNode);
        
        IfNode ifNode = (IfNode) module.getBody().get(0);
        assertTrue("Test should be CompareNode", ifNode.getTest() instanceof CompareNode);
        
        CompareNode compare = (CompareNode) ifNode.getTest();
        assertEquals("Should have 1 operator", 1, compare.getOps().size());
        assertEquals("Operator should be Gt", "Gt", compare.getOps().get(0));
        
        assertEquals("Body should have 1 statement", 1, ifNode.getBody().size());
        assertTrue("Body statement should be Assign", ifNode.getBody().get(0) instanceof AssignNode);
    }
    
    @Test
    public void testParseWhileLoop() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    While(\n" +
                "      test=Compare(\n" +
                "        left=Name(id='x', ctx=Load()),\n" +
                "        ops=[Lt()],\n" +
                "        comparators=[Constant(value=10)]),\n" +
                "      body=[\n" +
                "        AugAssign(\n" +
                "          target=Name(id='x', ctx=Store()),\n" +
                "          op=Add(),\n" +
                "          value=Constant(value=1))],\n" +
                "      orelse=[])],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertTrue("First node should be WhileNode", module.getBody().get(0) instanceof WhileNode);
        
        WhileNode whileNode = (WhileNode) module.getBody().get(0);
        assertTrue("Test should be CompareNode", whileNode.getTest() instanceof CompareNode);
        assertEquals("Body should have 1 statement", 1, whileNode.getBody().size());
        assertTrue("Body statement should be AugAssign", whileNode.getBody().get(0) instanceof AugAssignNode);
    }
    
    @Test
    public void testParseFunctionDefinition() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    FunctionDef(\n" +
                "      name='add',\n" +
                "      args=arguments(\n" +
                "        posonlyargs=[],\n" +
                "        args=[arg(arg='a'), arg(arg='b')],\n" +
                "        kwonlyargs=[],\n" +
                "        kw_defaults=[],\n" +
                "        defaults=[]),\n" +
                "      body=[\n" +
                "        Return(\n" +
                "          value=BinOp(\n" +
                "            left=Name(id='a', ctx=Load()),\n" +
                "            op=Add(),\n" +
                "            right=Name(id='b', ctx=Load())))],\n" +
                "      decorator_list=[])],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertTrue("First node should be FunctionDefNode", module.getBody().get(0) instanceof FunctionDefNode);
        
        FunctionDefNode funcDef = (FunctionDefNode) module.getBody().get(0);
        assertEquals("Function name should be 'add'", "add", funcDef.getName());
        
        assertNotNull("Arguments should not be null", funcDef.getArgs());
        assertEquals("Should have 2 arguments", 2, funcDef.getArgs().getArgs().size());
        assertEquals("First arg should be 'a'", "a", funcDef.getArgs().getArgs().get(0).getArg());
        assertEquals("Second arg should be 'b'", "b", funcDef.getArgs().getArgs().get(1).getArg());
        
        assertEquals("Body should have 1 statement", 1, funcDef.getBody().size());
        assertTrue("Body statement should be Return", funcDef.getBody().get(0) instanceof ReturnNode);
    }
    
    @Test
    public void testParseListCreation() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    Assign(\n" +
                "      targets=[Name(id='arr', ctx=Store())],\n" +
                "      value=List(\n" +
                "        elts=[Constant(value=1), Constant(value=2), Constant(value=3)],\n" +
                "        ctx=Load()))],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        AssignNode assign = (AssignNode) module.getBody().get(0);
        assertTrue("Value should be ListNode", assign.getValue() instanceof ListNode);
        
        ListNode list = (ListNode) assign.getValue();
        assertEquals("List should have 3 elements", 3, list.getElts().size());
    }
    
    @Test
    public void testParseFunctionCall() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    Assign(\n" +
                "      targets=[Name(id='result', ctx=Store())],\n" +
                "      value=Call(\n" +
                "        func=Name(id='add', ctx=Load()),\n" +
                "        args=[Name(id='x', ctx=Load()), Name(id='y', ctx=Load())],\n" +
                "        keywords=[]))],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        AssignNode assign = (AssignNode) module.getBody().get(0);
        assertTrue("Value should be CallNode", assign.getValue() instanceof CallNode);
        
        CallNode call = (CallNode) assign.getValue();
        assertTrue("Func should be NameNode", call.getFunc() instanceof NameNode);
        assertEquals("Should have 2 arguments", 2, call.getArgs().size());
    }
    
    @Test
    public void testParseArraySubscript() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    Assign(\n" +
                "      targets=[Name(id='x', ctx=Store())],\n" +
                "      value=Subscript(\n" +
                "        value=Name(id='arr', ctx=Load()),\n" +
                "        slice=Constant(value=0),\n" +
                "        ctx=Load()))],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        AssignNode assign = (AssignNode) module.getBody().get(0);
        assertTrue("Value should be SubscriptNode", assign.getValue() instanceof SubscriptNode);
        
        SubscriptNode subscript = (SubscriptNode) assign.getValue();
        assertTrue("Value should be NameNode", subscript.getValue() instanceof NameNode);
        assertTrue("Slice should be ConstantNode", subscript.getSlice() instanceof ConstantNode);
    }
    
    @Test
    public void testParseMultipleStatements() throws CompilationException {
        String astDump = "Module(\n" +
                "  body=[\n" +
                "    Assign(\n" +
                "      targets=[Name(id='x', ctx=Store())],\n" +
                "      value=Constant(value=5)),\n" +
                "    Assign(\n" +
                "      targets=[Name(id='y', ctx=Store())],\n" +
                "      value=Constant(value=10)),\n" +
                "    Assign(\n" +
                "      targets=[Name(id='z', ctx=Store())],\n" +
                "      value=BinOp(\n" +
                "        left=Name(id='x', ctx=Load()),\n" +
                "        op=Add(),\n" +
                "        right=Name(id='y', ctx=Load())))],\n" +
                "  type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertEquals("Module should have 3 statements", 3, module.getBody().size());
        
        assertTrue("All statements should be AssignNode", 
            module.getBody().stream().allMatch(node -> node instanceof AssignNode));
    }
}

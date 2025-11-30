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
    
    @Test
    public void testParseSimpleCompare() throws CompilationException {
        // Minimal test for Compare parsing
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      If(\n" +
                "         test=Compare(\n" +
                "            left=Name(id='a', ctx=Load()),\n" +
                "            ops=[\n" +
                "               Gt()],\n" +
                "            comparators=[\n" +
                "               Name(id='b', ctx=Load())]),\n" +
                "         body=[],\n" +
                "         orelse=[])],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertEquals("Module should have 1 statement", 1, module.getBody().size());
        assertTrue("Should be IfNode", module.getBody().get(0) instanceof IfNode);
        
        IfNode ifNode = (IfNode) module.getBody().get(0);
        assertTrue("Test should be CompareNode", ifNode.getTest() instanceof CompareNode);
        
        CompareNode compare = (CompareNode) ifNode.getTest();
        assertNotNull("Compare left should not be null", compare.getLeft());
        assertTrue("Compare left should be NameNode", compare.getLeft() instanceof NameNode);
        assertEquals("Compare left should be 'a'", "a", ((NameNode) compare.getLeft()).getId());
        
        assertEquals("Should have 1 comparator", 1, compare.getComparators().size());
        assertEquals("Comparator should be 'b'", "b", ((NameNode) compare.getComparators().get(0)).getId());
    }
    
    @Test
    public void testParseCompareAfterAssignments() throws CompilationException {
        // Test with assignments before the If statement
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='a', ctx=Store())],\n" +
                "         value=Constant(value=10)),\n" +
                "      If(\n" +
                "         test=Compare(\n" +
                "            left=Name(id='a', ctx=Load()),\n" +
                "            ops=[\n" +
                "               Gt()],\n" +
                "            comparators=[\n" +
                "               Name(id='b', ctx=Load())]),\n" +
                "         body=[],\n" +
                "         orelse=[])],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertEquals("Module should have 2 statements", 2, module.getBody().size());
        assertTrue("First should be AssignNode", module.getBody().get(0) instanceof AssignNode);
        assertTrue("Second should be IfNode", module.getBody().get(1) instanceof IfNode);
        
        IfNode ifNode = (IfNode) module.getBody().get(1);
        assertTrue("Test should be CompareNode", ifNode.getTest() instanceof CompareNode);
        
        CompareNode compare = (CompareNode) ifNode.getTest();
        assertNotNull("Compare left should not be null", compare.getLeft());
        assertTrue("Compare left should be NameNode", compare.getLeft() instanceof NameNode);
        assertEquals("Compare left should be 'a'", "a", ((NameNode) compare.getLeft()).getId());
    }
    
    @Test
    public void testParseCompareAfterThreeAssignments() throws CompilationException {
        // Test with 3 assignments before the If statement
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='a', ctx=Store())],\n" +
                "         value=Constant(value=10)),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='b', ctx=Store())],\n" +
                "         value=Constant(value=5)),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='result', ctx=Store())],\n" +
                "         value=Constant(value=0)),\n" +
                "      If(\n" +
                "         test=Compare(\n" +
                "            left=Name(id='a', ctx=Load()),\n" +
                "            ops=[\n" +
                "               Gt()],\n" +
                "            comparators=[\n" +
                "               Name(id='b', ctx=Load())]),\n" +
                "         body=[],\n" +
                "         orelse=[])],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertEquals("Module should have 4 statements", 4, module.getBody().size());
        assertTrue("Fourth should be IfNode", module.getBody().get(3) instanceof IfNode);
        
        IfNode ifNode = (IfNode) module.getBody().get(3);
        assertTrue("Test should be CompareNode", ifNode.getTest() instanceof CompareNode);
        
        CompareNode compare = (CompareNode) ifNode.getTest();
        assertNotNull("Compare left should not be null", compare.getLeft());
        assertTrue("Compare left should be NameNode", compare.getLeft() instanceof NameNode);
        assertEquals("Compare left should be 'a'", "a", ((NameNode) compare.getLeft()).getId());
    }
    
    @Test
    public void testParseIfWithBodyAndElse() throws CompilationException {
        // Test If with body containing BinOp and else clause
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      If(\n" +
                "         test=Compare(\n" +
                "            left=Name(id='a', ctx=Load()),\n" +
                "            ops=[\n" +
                "               Gt()],\n" +
                "            comparators=[\n" +
                "               Name(id='b', ctx=Load())]),\n" +
                "         body=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='result', ctx=Store())],\n" +
                "               value=BinOp(\n" +
                "                  left=Name(id='a', ctx=Load()),\n" +
                "                  op=Sub(),\n" +
                "                  right=Name(id='b', ctx=Load())))],\n" +
                "         orelse=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='result', ctx=Store())],\n" +
                "               value=BinOp(\n" +
                "                  left=Name(id='b', ctx=Load()),\n" +
                "                  op=Sub(),\n" +
                "                  right=Name(id='a', ctx=Load())))])],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertEquals("Module should have 1 statement", 1, module.getBody().size());
        assertTrue("Should be IfNode", module.getBody().get(0) instanceof IfNode);
        
        IfNode ifNode = (IfNode) module.getBody().get(0);
        assertNotNull("If test should not be null", ifNode.getTest());
        assertTrue("Test should be CompareNode, but was " + ifNode.getTest().getClass().getSimpleName(), 
            ifNode.getTest() instanceof CompareNode);
        
        CompareNode compare = (CompareNode) ifNode.getTest();
        assertNotNull("Compare left should not be null", compare.getLeft());
        assertTrue("Compare left should be NameNode", compare.getLeft() instanceof NameNode);
        
        assertEquals("Compare left should be 'a'", "a", ((NameNode) compare.getLeft()).getId());
        
        // Check if body
        assertEquals("If body should have 1 statement", 1, ifNode.getBody().size());
        
        // Check else body  
        assertEquals("Else body should have 1 statement", 1, ifNode.getOrelse().size());
    }
    
    @Test
    public void testParseIfElseWithMultiLineFormat() throws CompilationException {
        // This test uses the exact format from Python AST dump with multi-line indentation
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='a', ctx=Store())],\n" +
                "         value=Constant(value=10)),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='b', ctx=Store())],\n" +
                "         value=Constant(value=5)),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='result', ctx=Store())],\n" +
                "         value=Constant(value=0)),\n" +
                "      If(\n" +
                "         test=Compare(\n" +
                "            left=Name(id='a', ctx=Load()),\n" +
                "            ops=[\n" +
                "               Gt()],\n" +
                "            comparators=[\n" +
                "               Name(id='b', ctx=Load())]),\n" +
                "         body=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='result', ctx=Store())],\n" +
                "               value=BinOp(\n" +
                "                  left=Name(id='a', ctx=Load()),\n" +
                "                  op=Sub(),\n" +
                "                  right=Name(id='b', ctx=Load())))],\n" +
                "         orelse=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='result', ctx=Store())],\n" +
                "               value=BinOp(\n" +
                "                  left=Name(id='b', ctx=Load()),\n" +
                "                  op=Sub(),\n" +
                "                  right=Name(id='a', ctx=Load())))])],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 4 statements", 4, module.getBody().size());
        
        // Check first 3 are Assign
        assertTrue("First node should be AssignNode", module.getBody().get(0) instanceof AssignNode);
        assertTrue("Second node should be AssignNode", module.getBody().get(1) instanceof AssignNode);
        assertTrue("Third node should be AssignNode", module.getBody().get(2) instanceof AssignNode);
        
        // Check first assignment
        AssignNode assign1 = (AssignNode) module.getBody().get(0);
        assertEquals("First assign target should be 'a'", "a", 
            ((NameNode) assign1.getTargets().get(0)).getId());
        assertNotNull("First assign value should not be null", assign1.getValue());
        assertTrue("First assign value should be Constant", assign1.getValue() instanceof ConstantNode);
        assertEquals("First assign value should be 10", 10, ((ConstantNode) assign1.getValue()).getValue());
        
        // Check second assignment
        AssignNode assign2 = (AssignNode) module.getBody().get(1);
        assertEquals("Second assign target should be 'b'", "b", 
            ((NameNode) assign2.getTargets().get(0)).getId());
        assertNotNull("Second assign value should not be null", assign2.getValue());
        assertEquals("Second assign value should be 5", 5, ((ConstantNode) assign2.getValue()).getValue());
        
        // Check third assignment
        AssignNode assign3 = (AssignNode) module.getBody().get(2);
        assertEquals("Third assign target should be 'result'", "result", 
            ((NameNode) assign3.getTargets().get(0)).getId());
        assertNotNull("Third assign value should not be null", assign3.getValue());
        assertEquals("Third assign value should be 0", 0, ((ConstantNode) assign3.getValue()).getValue());
        
        // Check fourth is If
        assertTrue("Fourth node should be IfNode", module.getBody().get(3) instanceof IfNode);
        
        IfNode ifNode = (IfNode) module.getBody().get(3);
        
        // Check test condition
        assertTrue("Test should be CompareNode", ifNode.getTest() instanceof CompareNode);
        CompareNode compare = (CompareNode) ifNode.getTest();
        assertTrue("Compare left should be NameNode", compare.getLeft() instanceof NameNode);
        assertEquals("Compare left should be 'a'", "a", ((NameNode) compare.getLeft()).getId());
        assertEquals("Should have 1 operator", 1, compare.getOps().size());
        assertEquals("Operator should be Gt", "Gt", compare.getOps().get(0));
        assertEquals("Should have 1 comparator", 1, compare.getComparators().size());
        assertTrue("Comparator should be NameNode", compare.getComparators().get(0) instanceof NameNode);
        assertEquals("Comparator should be 'b'", "b", ((NameNode) compare.getComparators().get(0)).getId());
        
        // Check if body
        assertEquals("If body should have 1 statement", 1, ifNode.getBody().size());
        assertTrue("If body should be AssignNode", ifNode.getBody().get(0) instanceof AssignNode);
        AssignNode ifAssign = (AssignNode) ifNode.getBody().get(0);
        assertTrue("If body value should be BinOp", ifAssign.getValue() instanceof BinOpNode);
        BinOpNode ifBinOp = (BinOpNode) ifAssign.getValue();
        assertEquals("If body op should be Sub", "Sub", ifBinOp.getOp());
        
        // Check else body
        assertNotNull("Else body should not be null", ifNode.getOrelse());
        assertEquals("Else body should have 1 statement", 1, ifNode.getOrelse().size());
        assertTrue("Else body should be AssignNode", ifNode.getOrelse().get(0) instanceof AssignNode);
        AssignNode elseAssign = (AssignNode) ifNode.getOrelse().get(0);
        assertTrue("Else body value should be BinOp", elseAssign.getValue() instanceof BinOpNode);
        BinOpNode elseBinOp = (BinOpNode) elseAssign.getValue();
        assertEquals("Else body op should be Sub", "Sub", elseBinOp.getOp());
    }
}

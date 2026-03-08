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
    
    @Test
    public void testSimpleFunctionDefFollowedByAssign() throws CompilationException {
        // Simplest case: FunctionDef followed by Assign
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      FunctionDef(\n" +
                "         name='test',\n" +
                "         args=arguments(\n" +
                "            posonlyargs=[],\n" +
                "            args=[],\n" +
                "            kwonlyargs=[],\n" +
                "            kw_defaults=[],\n" +
                "            defaults=[]),\n" +
                "         body=[],\n" +
                "         decorator_list=[],\n" +
                "         type_params=[]),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='x', ctx=Store())],\n" +
                "         value=Constant(value=0))],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 2 statements", 2, module.getBody().size());
        assertTrue("First node should be FunctionDefNode", module.getBody().get(0) instanceof FunctionDefNode);
        assertTrue("Second node should be AssignNode", module.getBody().get(1) instanceof AssignNode);
    }
    
    @Test
    public void testFunctionDefWithBodyFollowedByAssign() throws CompilationException {
        // FunctionDef with body statements followed by module-level Assign
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      FunctionDef(\n" +
                "         name='test',\n" +
                "         args=arguments(\n" +
                "            posonlyargs=[],\n" +
                "            args=[],\n" +
                "            kwonlyargs=[],\n" +
                "            kw_defaults=[],\n" +
                "            defaults=[]),\n" +
                "         body=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='i', ctx=Store())],\n" +
                "               value=Constant(value=0))],\n" +
                "         decorator_list=[],\n" +
                "         type_params=[]),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='x', ctx=Store())],\n" +
                "         value=Constant(value=0))],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 2 statements", 2, module.getBody().size());
        assertTrue("First node should be FunctionDefNode", module.getBody().get(0) instanceof FunctionDefNode);
        
        FunctionDefNode funcDef = (FunctionDefNode) module.getBody().get(0);
        assertEquals("Function body should have 1 statement", 1, funcDef.getBody().size());
        
        assertTrue("Second node should be AssignNode", module.getBody().get(1) instanceof AssignNode);
    }
    
    @Test
    public void testFunctionDefWithTwoBodyStatementsFollowedByAssign() throws CompilationException {
        // FunctionDef with 2 body statements followed by module-level Assign
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      FunctionDef(\n" +
                "         name='test',\n" +
                "         args=arguments(\n" +
                "            posonlyargs=[],\n" +
                "            args=[],\n" +
                "            kwonlyargs=[],\n" +
                "            kw_defaults=[],\n" +
                "            defaults=[]),\n" +
                "         body=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='i', ctx=Store())],\n" +
                "               value=Constant(value=0)),\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='j', ctx=Store())],\n" +
                "               value=Constant(value=0))],\n" +
                "         decorator_list=[],\n" +
                "         type_params=[]),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='x', ctx=Store())],\n" +
                "         value=Constant(value=0))],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 2 statements", 2, module.getBody().size());
        assertTrue("First node should be FunctionDefNode", module.getBody().get(0) instanceof FunctionDefNode);
        
        FunctionDefNode funcDef = (FunctionDefNode) module.getBody().get(0);
        assertEquals("Function body should have 2 statements", 2, funcDef.getBody().size());
        
        assertTrue("Second node should be AssignNode", module.getBody().get(1) instanceof AssignNode);
    }
    
    @Test
    public void testFunctionDefWithArgsFollowedByAssign() throws CompilationException {
        // FunctionDef with arguments followed by module-level Assign
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      FunctionDef(\n" +
                "         name='test',\n" +
                "         args=arguments(\n" +
                "            posonlyargs=[],\n" +
                "            args=[\n" +
                "               arg(arg='outer'),\n" +
                "               arg(arg='inner'),\n" +
                "               arg(arg='result')],\n" +
                "            kwonlyargs=[],\n" +
                "            kw_defaults=[],\n" +
                "            defaults=[]),\n" +
                "         body=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='i', ctx=Store())],\n" +
                "               value=Constant(value=0)),\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='j', ctx=Store())],\n" +
                "               value=Constant(value=0))],\n" +
                "         decorator_list=[],\n" +
                "         type_params=[]),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='x', ctx=Store())],\n" +
                "         value=Constant(value=0))],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 2 statements", 2, module.getBody().size());
        assertTrue("First node should be FunctionDefNode", module.getBody().get(0) instanceof FunctionDefNode);
        
        FunctionDefNode funcDef = (FunctionDefNode) module.getBody().get(0);
        assertEquals("Function should have 3 args", 3, funcDef.getArgs().getArgs().size());
        assertEquals("Function body should have 2 statements", 2, funcDef.getBody().size());
        
        assertTrue("Second node should be AssignNode", module.getBody().get(1) instanceof AssignNode);
    }

    @Test
    public void testParseFunctionDefWithDecoratorListAndTypeParams() throws CompilationException {
        // Test parsing a FunctionDef followed by Assign and Expr (function call) at module level
        // This is the exact format from Python's AST dump with decorator_list and type_params
        String astDump = "Module(\n" +
                "   body=[\n" +
                "      FunctionDef(\n" +
                "         name='nestedWhileTest',\n" +
                "         args=arguments(\n" +
                "            posonlyargs=[],\n" +
                "            args=[\n" +
                "               arg(arg='outer'),\n" +
                "               arg(arg='inner'),\n" +
                "               arg(arg='result')],\n" +
                "            kwonlyargs=[],\n" +
                "            kw_defaults=[],\n" +
                "            defaults=[]),\n" +
                "         body=[\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='i', ctx=Store())],\n" +
                "               value=Constant(value=0)),\n" +
                "            Assign(\n" +
                "               targets=[\n" +
                "                  Name(id='j', ctx=Store())],\n" +
                "               value=Constant(value=0))],\n" +
                "         decorator_list=[],\n" +
                "         type_params=[]),\n" +
                "      Assign(\n" +
                "         targets=[\n" +
                "            Name(id='testResult', ctx=Store())],\n" +
                "         value=Constant(value=0)),\n" +
                "      Expr(\n" +
                "         value=Call(\n" +
                "            func=Name(id='nestedWhileTest', ctx=Load()),\n" +
                "            args=[\n" +
                "               Constant(value=3),\n" +
                "               Constant(value=4),\n" +
                "               Name(id='testResult', ctx=Load())],\n" +
                "            keywords=[]))],\n" +
                "   type_ignores=[])";
        
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);
        
        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 3 statements (FunctionDef, Assign, Expr)", 3, module.getBody().size());
        
        // Check first is FunctionDef
        assertTrue("First node should be FunctionDefNode", module.getBody().get(0) instanceof FunctionDefNode);
        FunctionDefNode funcDef = (FunctionDefNode) module.getBody().get(0);
        assertEquals("Function name should be 'nestedWhileTest'", "nestedWhileTest", funcDef.getName());
        assertEquals("Function should have 3 arguments", 3, funcDef.getArgs().getArgs().size());
        assertEquals("First arg should be 'outer'", "outer", funcDef.getArgs().getArgs().get(0).getArg());
        assertEquals("Function body should have 2 statements", 2, funcDef.getBody().size());
        
        // Check second is Assign (testResult = 0)
        assertTrue("Second node should be AssignNode", module.getBody().get(1) instanceof AssignNode);
        AssignNode assign = (AssignNode) module.getBody().get(1);
        assertEquals("Assign target should be 'testResult'", "testResult", 
            ((NameNode) assign.getTargets().get(0)).getId());
        
        // Check third is Expr with Call
        assertTrue("Third node should be ExprNode", module.getBody().get(2) instanceof ExprNode);
        ExprNode expr = (ExprNode) module.getBody().get(2);
        assertTrue("Expr value should be CallNode", expr.getValue() instanceof CallNode);
        CallNode call = (CallNode) expr.getValue();
        assertEquals("Call function should be 'nestedWhileTest'", "nestedWhileTest", 
            ((NameNode) call.getFunc()).getId());
        assertEquals("Call should have 3 arguments", 3, call.getArgs().size());
    }

    // =========================================================================
    // OOP – ClassDef parsing tests
    // =========================================================================

    @Test
    public void testParseSimpleClassDef() throws CompilationException {
        String astDump =
            "Module(\n" +
            "  body=[\n" +
            "    ClassDef(\n" +
            "      name='Person',\n" +
            "      bases=[],\n" +
            "      keywords=[],\n" +
            "      body=[\n" +
            "        FunctionDef(\n" +
            "          name='__init__',\n" +
            "          args=arguments(\n" +
            "            posonlyargs=[],\n" +
            "            args=[arg(arg='self'), arg(arg='name'), arg(arg='age')],\n" +
            "            kwonlyargs=[],\n" +
            "            kw_defaults=[],\n" +
            "            defaults=[]),\n" +
            "          body=[\n" +
            "            Assign(\n" +
            "              targets=[\n" +
            "                Attribute(\n" +
            "                  value=Name(id='self', ctx=Load()),\n" +
            "                  attr='name',\n" +
            "                  ctx=Store())],\n" +
            "              value=Name(id='name', ctx=Load())),\n" +
            "            Assign(\n" +
            "              targets=[\n" +
            "                Attribute(\n" +
            "                  value=Name(id='self', ctx=Load()),\n" +
            "                  attr='age',\n" +
            "                  ctx=Store())],\n" +
            "              value=Name(id='age', ctx=Load()))],\n" +
            "          decorator_list=[])],\n" +
            "      decorator_list=[])],\n" +
            "  type_ignores=[])";

        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);

        assertNotNull("Module should not be null", module);
        assertEquals("Module should have 1 statement", 1, module.getBody().size());

        AstNode firstNode = module.getBody().get(0);
        assertTrue("First node should be ClassDefNode", firstNode instanceof ClassDefNode);

        ClassDefNode classDef = (ClassDefNode) firstNode;
        assertEquals("Class name should be 'Person'", "Person", classDef.getName());
        assertEquals("Class body should have 1 method", 1, classDef.getBody().size());

        assertTrue("Class body[0] should be FunctionDefNode",
            classDef.getBody().get(0) instanceof FunctionDefNode);
        FunctionDefNode initMethod = (FunctionDefNode) classDef.getBody().get(0);
        assertEquals("Method name should be '__init__'", "__init__", initMethod.getName());
        assertEquals("__init__ should have 3 args (self, name, age)",
            3, initMethod.getArgs().getArgs().size());
        assertEquals("First arg should be 'self'", "self",
            initMethod.getArgs().getArgs().get(0).getArg());
    }

    @Test
    public void testParseClassWithMultipleMethods() throws CompilationException {
        String astDump =
            "Module(\n" +
            "  body=[\n" +
            "    ClassDef(\n" +
            "      name='Calculator',\n" +
            "      bases=[],\n" +
            "      keywords=[],\n" +
            "      body=[\n" +
            "        FunctionDef(\n" +
            "          name='__init__',\n" +
            "          args=arguments(\n" +
            "            posonlyargs=[],\n" +
            "            args=[arg(arg='self'), arg(arg='value')],\n" +
            "            kwonlyargs=[],\n" +
            "            kw_defaults=[],\n" +
            "            defaults=[]),\n" +
            "          body=[\n" +
            "            Assign(\n" +
            "              targets=[\n" +
            "                Attribute(\n" +
            "                  value=Name(id='self', ctx=Load()),\n" +
            "                  attr='value',\n" +
            "                  ctx=Store())],\n" +
            "              value=Name(id='value', ctx=Load()))],\n" +
            "          decorator_list=[]),\n" +
            "        FunctionDef(\n" +
            "          name='get_value',\n" +
            "          args=arguments(\n" +
            "            posonlyargs=[],\n" +
            "            args=[arg(arg='self')],\n" +
            "            kwonlyargs=[],\n" +
            "            kw_defaults=[],\n" +
            "            defaults=[]),\n" +
            "          body=[\n" +
            "            Return(\n" +
            "              value=Attribute(\n" +
            "                value=Name(id='self', ctx=Load()),\n" +
            "                attr='value',\n" +
            "                ctx=Load()))],\n" +
            "          decorator_list=[])],\n" +
            "      decorator_list=[])],\n" +
            "  type_ignores=[])";

        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);

        ClassDefNode classDef = (ClassDefNode) module.getBody().get(0);
        assertEquals("Class name should be 'Calculator'", "Calculator", classDef.getName());
        assertEquals("Class body should have 2 methods", 2, classDef.getBody().size());

        FunctionDefNode initMethod = (FunctionDefNode) classDef.getBody().get(0);
        assertEquals("First method should be '__init__'", "__init__", initMethod.getName());
        assertEquals("__init__ body has 1 statement", 1, initMethod.getBody().size());
        assertTrue("__init__ body[0] should be AssignNode",
            initMethod.getBody().get(0) instanceof AssignNode);

        AssignNode assignStmt = (AssignNode) initMethod.getBody().get(0);
        assertTrue("Target should be AttributeNode",
            assignStmt.getTargets().get(0) instanceof AttributeNode);
        AttributeNode attrTarget = (AttributeNode) assignStmt.getTargets().get(0);
        assertEquals("Attribute name should be 'value'", "value", attrTarget.getAttr());
        assertEquals("Object should be 'self'", "self",
            ((NameNode) attrTarget.getValue()).getId());

        FunctionDefNode getValueMethod = (FunctionDefNode) classDef.getBody().get(1);
        assertEquals("Second method should be 'get_value'", "get_value", getValueMethod.getName());
        assertEquals("get_value should have 1 arg", 1, getValueMethod.getArgs().getArgs().size());
        assertEquals("get_value arg should be 'self'", "self",
            getValueMethod.getArgs().getArgs().get(0).getArg());

        assertTrue("get_value body[0] should be ReturnNode",
            getValueMethod.getBody().get(0) instanceof ReturnNode);
        ReturnNode ret = (ReturnNode) getValueMethod.getBody().get(0);
        assertTrue("Return value should be AttributeNode", ret.getValue() instanceof AttributeNode);
        AttributeNode retAttr = (AttributeNode) ret.getValue();
        assertEquals("Returned attr should be 'value'", "value", retAttr.getAttr());
    }

    @Test
    public void testParseAttributeAssignment() throws CompilationException {
        // self.name = value
        String astDump =
            "Module(\n" +
            "  body=[\n" +
            "    Assign(\n" +
            "      targets=[\n" +
            "        Attribute(\n" +
            "          value=Name(id='self', ctx=Load()),\n" +
            "          attr='name',\n" +
            "          ctx=Store())],\n" +
            "      value=Name(id='name', ctx=Load()))],\n" +
            "  type_ignores=[])";

        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);

        assertEquals("Should have 1 statement", 1, module.getBody().size());
        assertTrue("Should be AssignNode", module.getBody().get(0) instanceof AssignNode);

        AssignNode assign = (AssignNode) module.getBody().get(0);
        assertTrue("Target should be AttributeNode",
            assign.getTargets().get(0) instanceof AttributeNode);

        AttributeNode attr = (AttributeNode) assign.getTargets().get(0);
        assertEquals("Attr name should be 'name'", "name", attr.getAttr());
        assertEquals("Context should be Store", "Store", attr.getCtx());
        assertTrue("Value should be NameNode", attr.getValue() instanceof NameNode);
        assertEquals("Object should be 'self'", "self", ((NameNode) attr.getValue()).getId());

        assertTrue("RHS should be NameNode", assign.getValue() instanceof NameNode);
        assertEquals("RHS name should be 'name'", "name", ((NameNode) assign.getValue()).getId());
    }

    @Test
    public void testParseAttributeRead() throws CompilationException {
        // x = obj.value
        String astDump =
            "Module(\n" +
            "  body=[\n" +
            "    Assign(\n" +
            "      targets=[Name(id='x', ctx=Store())],\n" +
            "      value=Attribute(\n" +
            "        value=Name(id='obj', ctx=Load()),\n" +
            "        attr='value',\n" +
            "        ctx=Load()))],\n" +
            "  type_ignores=[])";

        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);

        AssignNode assign = (AssignNode) module.getBody().get(0);
        assertTrue("Value should be AttributeNode", assign.getValue() instanceof AttributeNode);

        AttributeNode attr = (AttributeNode) assign.getValue();
        assertEquals("Attr should be 'value'", "value", attr.getAttr());
        assertEquals("Context should be Load", "Load", attr.getCtx());
        assertEquals("Object should be 'obj'", "obj", ((NameNode) attr.getValue()).getId());
    }

    @Test
    public void testParseMethodCallAsStatement() throws CompilationException {
        // obj.update(5)
        String astDump =
            "Module(\n" +
            "  body=[\n" +
            "    Expr(\n" +
            "      value=Call(\n" +
            "        func=Attribute(\n" +
            "          value=Name(id='obj', ctx=Load()),\n" +
            "          attr='update',\n" +
            "          ctx=Load()),\n" +
            "        args=[Constant(value=5)],\n" +
            "        keywords=[]))],\n" +
            "  type_ignores=[])";

        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);

        assertEquals("Should have 1 statement", 1, module.getBody().size());
        assertTrue("Should be ExprNode", module.getBody().get(0) instanceof ExprNode);

        ExprNode exprNode = (ExprNode) module.getBody().get(0);
        assertTrue("Value should be CallNode", exprNode.getValue() instanceof CallNode);

        CallNode call = (CallNode) exprNode.getValue();
        assertTrue("func should be AttributeNode", call.getFunc() instanceof AttributeNode);

        AttributeNode funcAttr = (AttributeNode) call.getFunc();
        assertEquals("Method name should be 'update'", "update", funcAttr.getAttr());
        assertEquals("Object should be 'obj'", "obj",
            ((NameNode) funcAttr.getValue()).getId());
        assertEquals("Should have 1 argument", 1, call.getArgs().size());
        assertTrue("Argument should be ConstantNode", call.getArgs().get(0) instanceof ConstantNode);
    }
}

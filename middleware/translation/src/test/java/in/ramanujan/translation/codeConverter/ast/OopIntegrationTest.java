package in.ramanujan.translation.codeConverter.ast;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.ClassDefinition;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.ObjectInstance;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for OOP (class / object) compilation support.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>ClassDef AST nodes are parsed correctly by {@link AstParser}.</li>
 *   <li>{@link PythonAstToRuleEngineInputConverter} produces the expected
 *       {@link ClassDefinition}, {@link ObjectInstance}, method {@link FunctionCall}s,
 *       and field {@link Variable}s in the {@link RuleEngineInput}.</li>
 *   <li>Class objects are passed by reference to functions (field Variable IDs are
 *       shared, not copied).</li>
 * </ul>
 *
 * <p>All tests use the text-based {@link AstParser} with hand-crafted AST dump strings
 * so that they work without a live Python interpreter.
 */
public class OopIntegrationTest {

    private CodeConverter codeConverter;
    private RuleEngineInput ruleEngineInput;
    private DebugLevelCodeCreator debugLevelCodeCreator;
    private List<String> variableScope;

    @Before
    public void setUp() {
        codeConverter = new CodeConverter(new CodeConverterLogicFactory(), new StringUtils());
        ruleEngineInput = new RuleEngineInput();
        debugLevelCodeCreator = new NoConcatImpl();
        variableScope = new ArrayList<>();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Runs the full parse + convert pipeline for the given AST dump string.
     */
    private List<Command> convertAstDump(String astDump) throws CompilationException {
        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);

        PythonAstToRuleEngineInputConverter converter = new PythonAstToRuleEngineInputConverter(
            codeConverter, ruleEngineInput, debugLevelCodeCreator,
            new HashMap<>(), new Integer[]{0}
        );

        variableScope.add("");
        return converter.convert(module, variableScope);
    }

    private ClassDefinition findClassDef(String className) {
        return ruleEngineInput.getClassDefinitions().stream()
            .filter(cd -> className.equals(cd.getClassName()))
            .findFirst().orElse(null);
    }

    private ObjectInstance findObjectInstance(String instanceName) {
        return ruleEngineInput.getObjectInstances().stream()
            .filter(oi -> instanceName.equals(oi.getInstanceName()))
            .findFirst().orElse(null);
    }

    private FunctionCall findFunctionCall(String functionId) {
        return ruleEngineInput.getFunctionCalls().stream()
            .filter(fc -> functionId.equals(fc.getId()))
            .findFirst().orElse(null);
    }

    private Variable findVariable(String name) {
        return ruleEngineInput.getVariables().stream()
            .filter(v -> name.equals(v.getName()))
            .findFirst().orElse(null);
    }

    // =========================================================================
    // ClassDef parsing tests (AST layer)
    // =========================================================================

    @Test
    public void testAstParserProducesClassDefNode() throws CompilationException {
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
            "            args=[arg(arg='self'), arg(arg='name')],\n" +
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
            "              value=Name(id='name', ctx=Load()))],\n" +
            "          decorator_list=[])],\n" +
            "      decorator_list=[])],\n" +
            "  type_ignores=[])";

        AstParser parser = new AstParser();
        ModuleNode module = parser.parse(astDump);

        assertEquals("Module has one statement", 1, module.getBody().size());
        assertTrue("Statement is ClassDefNode", module.getBody().get(0) instanceof ClassDefNode);

        ClassDefNode classDef = (ClassDefNode) module.getBody().get(0);
        assertEquals("Class name is 'Person'", "Person", classDef.getName());
        assertEquals("Class has one method", 1, classDef.getBody().size());
        assertTrue("Class method is FunctionDefNode",
            classDef.getBody().get(0) instanceof FunctionDefNode);
    }

    // =========================================================================
    // Class definition compilation tests
    // =========================================================================

    /**
     * A class with one field and a constructor should produce:
     * <ul>
     *   <li>A {@link ClassDefinition} with the correct className and fieldNames.</li>
     *   <li>A {@link FunctionCall} named {@code Person___init__} with parameters for
     *       the field variable and the explicit {@code name} argument.</li>
     * </ul>
     */
    @Test
    public void testClassDefinitionIsCreated() throws CompilationException {
        String astDump = buildPersonClassAstDump();
        convertAstDump(astDump);

        ClassDefinition cd = findClassDef("Person");
        assertNotNull("ClassDefinition should be created for 'Person'", cd);
        assertEquals("Class name should be 'Person'", "Person", cd.getClassName());
        assertEquals("Should have 1 field ('name')", 1, cd.getFieldNames().size());
        assertEquals("Field should be 'name'", "name", cd.getFieldNames().get(0));
    }

    @Test
    public void testConstructorFunctionCallIsRegistered() throws CompilationException {
        String astDump = buildPersonClassAstDump();
        convertAstDump(astDump);

        ClassDefinition cd = findClassDef("Person");
        assertNotNull("ClassDefinition must exist", cd);
        assertEquals("Constructor function ID should be 'Person___init__'",
            "Person___init__", cd.getConstructorFunctionId());

        FunctionCall ctorCall = findFunctionCall("Person___init__");
        assertNotNull("FunctionCall for __init__ should be in RuleEngineInput", ctorCall);
        // Arguments: [self_name_param, name_param]
        assertNotNull("Constructor should have arguments", ctorCall.getArguments());
        assertEquals("Constructor should have 2 arguments (field + explicit param)",
            2, ctorCall.getArguments().size());
    }

    @Test
    public void testNonConstructorMethodIsRegistered() throws CompilationException {
        String astDump = buildPersonClassWithGreetAstDump();
        convertAstDump(astDump);

        ClassDefinition cd = findClassDef("Person");
        assertNotNull("ClassDefinition must exist", cd);
        assertEquals("Should have 1 non-constructor method", 1, cd.getMethodFunctionIds().size());
        assertEquals("Method function ID should be 'Person_greet'",
            "Person_greet", cd.getMethodFunctionIds().get(0));

        FunctionCall greetCall = findFunctionCall("Person_greet");
        assertNotNull("FunctionCall for greet should be in RuleEngineInput", greetCall);
        // Arguments: [self_name_param] only (no extra explicit params)
        assertEquals("greet should have 1 argument (field param for 'name')",
            1, greetCall.getArguments().size());
    }

    // =========================================================================
    // Object instantiation tests
    // =========================================================================

    @Test
    public void testObjectInstantiationCreatesObjectInstance() throws CompilationException {
        String astDump = buildPersonInstantiationAstDump();
        convertAstDump(astDump);

        ObjectInstance oi = findObjectInstance("p");
        assertNotNull("ObjectInstance should be created for 'p'", oi);
        assertEquals("Instance class should be 'Person'", "Person", oi.getClassName());
        assertEquals("Instance should have 1 field", 1, oi.getFieldVariableIds().size());
        assertTrue("FieldVariableIds should contain 'name'",
            oi.getFieldVariableIds().containsKey("name"));
    }

    @Test
    public void testObjectInstantiationCreatesFieldVariables() throws CompilationException {
        String astDump = buildPersonInstantiationAstDump();
        convertAstDump(astDump);

        Variable fieldVar = findVariable("p_name");
        assertNotNull("Field variable 'p_name' should be created", fieldVar);
    }

    // =========================================================================
    // Copy-by-reference tests
    // =========================================================================

    /**
     * When an object is passed to a method or function, the field Variable IDs are
     * passed directly (by reference).  If two calls to the same method use the same
     * object, both must use the same field Variable IDs.
     */
    @Test
    public void testFieldVariableIdsAreSharedByReference() throws CompilationException {
        String astDump = buildPersonInstantiationAstDump();
        convertAstDump(astDump);

        ObjectInstance oi = findObjectInstance("p");
        assertNotNull("ObjectInstance must exist", oi);

        // The field variable ID stored in the ObjectInstance must match the Variable's ID
        String nameFieldId = oi.getFieldVariableIds().get("name");
        assertNotNull("Field 'name' must have a Variable ID", nameFieldId);

        Variable fieldVar = findVariable("p_name");
        assertNotNull("Variable 'p_name' must exist", fieldVar);
        assertEquals("ObjectInstance field ID must match the Variable ID (reference semantics)",
            fieldVar.getId(), nameFieldId);
    }

    /**
     * When the constructor is called via object instantiation, the field Variable IDs
     * from the ObjectInstance should appear as the first arguments to the constructor
     * FunctionCall (ensuring pass-by-reference for those fields).
     */
    @Test
    public void testConstructorCallReceivesFieldVariablesByReference()
            throws CompilationException {
        String astDump = buildPersonInstantiationAstDump();
        List<Command> commands = convertAstDump(astDump);

        // The instantiation command's FunctionCall should reference the field variable
        ObjectInstance oi = findObjectInstance("p");
        assertNotNull("ObjectInstance must exist", oi);
        String nameFieldId = oi.getFieldVariableIds().get("name");

        // Find a command whose FunctionCall targets Person___init__
        Command initCommand = commands.stream()
            .filter(c -> c.getFunctionCall() != null &&
                         "Person___init__".equals(c.getFunctionCall().getId()))
            .findFirst().orElse(null);

        assertNotNull("A command calling Person___init__ must exist", initCommand);
        List<String> callArgs = initCommand.getFunctionCall().getArguments();
        assertTrue("Constructor call args must include the field Variable ID by reference",
            callArgs.contains(nameFieldId));
    }

    // =========================================================================
    // AST dump builders (no Python runtime required)
    // =========================================================================

    /**
     * Produces the AST dump for:
     * <pre>
     * class Person:
     *     def __init__(self, name):
     *         self.name = name
     * </pre>
     */
    private String buildPersonClassAstDump() {
        return
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
            "            args=[arg(arg='self'), arg(arg='name')],\n" +
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
            "              value=Name(id='name', ctx=Load()))],\n" +
            "          decorator_list=[])],\n" +
            "      decorator_list=[])],\n" +
            "  type_ignores=[])";
    }

    /**
     * Produces the AST dump for:
     * <pre>
     * class Person:
     *     def __init__(self, name):
     *         self.name = name
     *     def greet(self):
     *         return self.name
     * </pre>
     */
    private String buildPersonClassWithGreetAstDump() {
        return
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
            "            args=[arg(arg='self'), arg(arg='name')],\n" +
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
            "              value=Name(id='name', ctx=Load()))],\n" +
            "          decorator_list=[]),\n" +
            "        FunctionDef(\n" +
            "          name='greet',\n" +
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
            "                attr='name',\n" +
            "                ctx=Load()))],\n" +
            "          decorator_list=[])],\n" +
            "      decorator_list=[])],\n" +
            "  type_ignores=[])";
    }

    /**
     * Produces the AST dump for:
     * <pre>
     * class Person:
     *     def __init__(self, name):
     *         self.name = name
     *
     * p = Person("Alice")
     * </pre>
     */
    private String buildPersonInstantiationAstDump() {
        return
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
            "            args=[arg(arg='self'), arg(arg='name')],\n" +
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
            "              value=Name(id='name', ctx=Load()))],\n" +
            "          decorator_list=[])],\n" +
            "      decorator_list=[]),\n" +
            "    Assign(\n" +
            "      targets=[Name(id='p', ctx=Store())],\n" +
            "      value=Call(\n" +
            "        func=Name(id='Person', ctx=Load()),\n" +
            "        args=[Constant(value='Alice')],\n" +
            "        keywords=[]))],\n" +
            "  type_ignores=[])";
    }
}

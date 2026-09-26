package in.ramanujan.translation;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.rule.engine.RuleEngineInputProtoSerializer;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PythonMultiFileImportTest {

    private void interpretAndExecute(String mainCode,
                                     Map<String, String> files,
                                     Map<String, Variable> variableMap,
                                     Map<String, Array> arrayMap) throws Exception {
        CodeConverter codeConverter = new CodeConverter(new CodeConverterLogicFactory(), new StringUtils());
        codeConverter.setFilesMap(files);

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

        ActualDebugCodeCreator debugLevelCodeCreator = new ActualDebugCodeCreator("", 0);
        Map<Integer, RuleEngineInputUnits> functionFrameVariableMap = new HashMap<>();
        Integer[] frameVariableCounterId = {0};
        List<String> variableScope = new ArrayList<>();

        List<Command> commands = codeConverter.interpretPython(
                mainCode,
                files,
                ruleEngineInput,
                variableScope,
                debugLevelCodeCreator,
                functionFrameVariableMap,
                frameVariableCounterId
        );

        for (Variable variable : ruleEngineInput.getVariables()) {
            variableMap.put(variable.getId(), variable);
        }
        for (Array array : ruleEngineInput.getArrays()) {
            arrayMap.put(array.getId(), array);
        }

        if (!commands.isEmpty()) {
            NativeProcessor processor = new NativeProcessor();
            byte[] protoBytes = RuleEngineInputProtoSerializer.serialize(ruleEngineInput);
            processor.process(protoBytes, commands.get(0).getId());
            resolveVariablesFromNativeProcessor(processor, variableMap, arrayMap);
        }
    }

    @SuppressWarnings("unchecked")
    private void resolveVariablesFromNativeProcessor(NativeProcessor nativeProcessor,
                                                     Map<String, Variable> variableMap,
                                                     Map<String, Array> arrayMap) {
        if (nativeProcessor.jniObject == null) {
            return;
        }
        for (Object en : nativeProcessor.jniObject.entrySet()) {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) en;
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("arrayIndex".equalsIgnoreCase(key)) {
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
                Variable variable = variableMap.get(key);
                if (variable != null) {
                    variable.setValue(value);
                }
            }
        }
    }

    private Variable findVariableByName(Map<String, Variable> variableMap, String name) {
        for (Variable var : variableMap.values()) {
            if (name.equals(var.getName())) {
                return var;
            }
        }
        return null;
    }

    @Test
    public void testBasicImport() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("math_helper.py",
                "def add(a, b):\n" +
                "    temp = a + b\n" +
                "    return temp\n"
        );

        String mainCode =
                "import math_helper\n" +
                "res = 0\n" +
                "res = math_helper.add(10, 20)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable resVar = findVariableByName(variableMap, "res");
        assertNotNull("Variable 'res' should exist", resVar);
        assertEquals(30.0, ((Number) resVar.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testImportWithAlias() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("math_helper.py",
                "def add(a, b):\n" +
                "    temp = a + b\n" +
                "    return temp\n"
        );

        String mainCode =
                "import math_helper as mh\n" +
                "res = 0\n" +
                "res = mh.add(5, 7)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable resVar = findVariableByName(variableMap, "res");
        assertNotNull("Variable 'res' should exist", resVar);
        assertEquals(12.0, ((Number) resVar.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testFromImportFunction() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("math_helper.py",
                "def multiply(a, b):\n" +
                "    temp = a * b\n" +
                "    return temp\n"
        );

        String mainCode =
                "from math_helper import multiply\n" +
                "res = 0\n" +
                "res = multiply(6, 7)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable resVar = findVariableByName(variableMap, "res");
        assertNotNull("Variable 'res' should exist", resVar);
        assertEquals(42.0, ((Number) resVar.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testFromImportWithAlias() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("math_helper.py",
                "def subtract(a, b):\n" +
                "    temp = a - b\n" +
                "    return temp\n"
        );

        String mainCode =
                "from math_helper import subtract as sub\n" +
                "res = 0\n" +
                "res = sub(20, 8)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable resVar = findVariableByName(variableMap, "res");
        assertNotNull("Variable 'res' should exist", resVar);
        assertEquals(12.0, ((Number) resVar.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testWildcardImport() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("calc.py",
                "def add(a, b):\n" +
                "    temp = a + b\n" +
                "    return temp\n" +
                "def sub(a, b):\n" +
                "    temp = a - b\n" +
                "    return temp\n"
        );

        String mainCode =
                "from calc import *\n" +
                "r1 = 0\n" +
                "r2 = 0\n" +
                "r1 = add(10, 5)\n" +
                "r2 = sub(10, 5)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable r1Var = findVariableByName(variableMap, "r1");
        Variable r2Var = findVariableByName(variableMap, "r2");
        assertNotNull("Variable 'r1' should exist", r1Var);
        assertNotNull("Variable 'r2' should exist", r2Var);
        assertEquals(15.0, ((Number) r1Var.getValue()).doubleValue(), 0.001);
        assertEquals(5.0, ((Number) r2Var.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testInternalHelperCallWithinImportedModule() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("geometry.py",
                "def square(x):\n" +
                "    temp = x * x\n" +
                "    return temp\n" +
                "def area_square(side):\n" +
                "    s = square(side)\n" +
                "    return s\n"
        );

        String mainCode =
                "import geometry\n" +
                "res = 0\n" +
                "res = geometry.area_square(4)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable resVar = findVariableByName(variableMap, "res");
        assertNotNull("Variable 'res' should exist", resVar);
        assertEquals(16.0, ((Number) resVar.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testTupleUnpackingFromImportedModule() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("coords.py",
                "def get_coords():\n" +
                "    x = 100\n" +
                "    y = 200\n" +
                "    return x, y\n"
        );

        String mainCode =
                "import coords\n" +
                "cx, cy = coords.get_coords()\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable cxVar = findVariableByName(variableMap, "cx");
        Variable cyVar = findVariableByName(variableMap, "cy");
        assertNotNull("Variable 'cx' should exist", cxVar);
        assertNotNull("Variable 'cy' should exist", cyVar);
        assertEquals(100.0, ((Number) cxVar.getValue()).doubleValue(), 0.001);
        assertEquals(200.0, ((Number) cyVar.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testTransitiveImports() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("base_ops.py",
                "def double_val(x):\n" +
                "    temp = x * 2\n" +
                "    return temp\n"
        );
        files.put("service.py",
                "import base_ops\n" +
                "def compute(x):\n" +
                "    d = base_ops.double_val(x)\n" +
                "    ans = d + 1\n" +
                "    return ans\n"
        );

        String mainCode =
                "import service\n" +
                "final_res = 0\n" +
                "final_res = service.compute(5)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable resVar = findVariableByName(variableMap, "final_res");
        assertNotNull("Variable 'final_res' should exist", resVar);
        assertEquals(11.0, ((Number) resVar.getValue()).doubleValue(), 0.001);
    }

    @Test
    public void testMultipleModulesWithoutNameCollision() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("mod_a.py",
                "def calc(x):\n" +
                "    temp = x + 10\n" +
                "    return temp\n"
        );
        files.put("mod_b.py",
                "def calc(x):\n" +
                "    temp = x * 10\n" +
                "    return temp\n"
        );

        String mainCode =
                "import mod_a\n" +
                "import mod_b\n" +
                "res_a = 0\n" +
                "res_b = 0\n" +
                "res_a = mod_a.calc(5)\n" +
                "res_b = mod_b.calc(5)\n";

        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        interpretAndExecute(mainCode, files, variableMap, arrayMap);

        Variable resAVar = findVariableByName(variableMap, "res_a");
        Variable resBVar = findVariableByName(variableMap, "res_b");
        assertNotNull("Variable 'res_a' should exist", resAVar);
        assertNotNull("Variable 'res_b' should exist", resBVar);
        assertEquals(15.0, ((Number) resAVar.getValue()).doubleValue(), 0.001);
        assertEquals(50.0, ((Number) resBVar.getValue()).doubleValue(), 0.001);
    }
}

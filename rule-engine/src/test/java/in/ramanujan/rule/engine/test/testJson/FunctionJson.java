package in.ramanujan.rule.engine.test.testJson;

public class FunctionJson {
    public static String basicTestInput = "{\n" +
            "    \"variables\": [\n" +
            "        {\n" +
            "            \"id\": \"x\",\n" +
            "            \"name\": \"x\",\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"y\",\n" +
            "            \"name\": \"y\",\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"z\",\n" +
            "            \"name\": \"z\",\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"commands\": [\n" +
            "        {\n" +
            "          \"id\":\"com1\",\n" +
            "          \"operation\":\"op1\",\n" +
            "          \"nextId\":\"com2\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\":\"com2\",\n" +
            "          \"functionCall\":{\n" +
            "              \"id\":\"func1\",\n" +
            "              \"arguments\":[\"x\"]\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"command1Op1\",\n" +
            "            \"variableId\":\"x\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"command2Op1\",\n" +
            "            \"constant\":\"constantId1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"comm3\",\n" +
            "            \"functionCall\":{\n" +
            "                \"id\":\"func2\",\n" +
            "                \"arguments\":[\"y\"]\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"comm4\",\n" +
            "            \"operation\":\"op2\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"command1Op2\",\n" +
            "            \"variableId\":\"z\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"command2Op2\",\n" +
            "            \"constant\":\"constantId2\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"ifBlocks\": [\n" +
            "        \n" +
            "    ],\n" +
            "    \"operations\": [\n" +
            "        {\n" +
            "            \"id\": \"op1\",\n" +
            "            \"operatorType\": \"=\",\n" +
            "            \"operand1\": \"command1Op1\",\n" +
            "            \"operand2\": \"command2Op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"op2\",\n" +
            "            \"operatorType\":\"=\",\n" +
            "            \"operand1\":\"command1Op2\",\n" +
            "            \"operand2\":\"command2Op2\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"conditions\": [\n" +
            "        \n" +
            "    ],\n" +
            "    \"constants\": [\n" +
            "        {\n" +
            "            \"id\": \"constantId1\",\n" +
            "            \"value\": 10,\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"constantId2\",\n" +
            "            \"value\": 20,\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"constantId3\",\n" +
            "            \"value\": 10,\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"functionCalls\": [\n" +
            "        {\n" +
            "            \"id\": \"func1\",\n" +
            "            \"arguments\": [\n" +
            "                \"y\"\n" +
            "            ],\n" +
            "            \"firstCommandId\":\"comm3\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"func2\",\n" +
            "            \"arguments\": [\n" +
            "                \"z\"\n" +
            "            ],\n" +
            "\"firstCommandId\":\"comm4\""+
            "        }\n" +
            "    ]\n" +
            "}";

    public static String basicTestFirstCommandId = "com1";


    public static String arrayInFunctionTest = "{\n" +
            "\"variables\":[\n" +
            "{\n" +
            "\"id\":\"1b87b2ce-7521-49bd-9f7f-b4444a4a08e4\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable\",\n" +
            "\"name\":\"x\",\n" +
            "\"dataType\":\"integer\",\n" +
            "\"value\":null\n" +
            "}\n" +
            "],\n" +
            "\"commands\":[\n" +
            "{\n" +
            "\"id\":\"command_753ab9b6-48ce-489d-891d-7c866c698a0a\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":\"command_cd10a6ea-c6c5-4e11-8f49-55cac7b7bdc5\",\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_cd10a6ea-c6c5-4e11-8f49-55cac7b7bdc5\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":\"command_43abe5ed-1524-4790-9bfa-a03840cd7d8c\",\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_43abe5ed-1524-4790-9bfa-a03840cd7d8c\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":\"command_7201c9f6-9997-4fe2-a670-78a849868e71\",\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":\"361df2e3-b00a-4a46-b54a-9d4771a1e5fb\",\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_1c977a1e-5896-4d98-9636-9dafb1319308\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":null,\n" +
            "\"variableId\":\"1b87b2ce-7521-49bd-9f7f-b4444a4a08e4\",\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_5bcb38c9-c6d7-4e4e-96ed-39d7aa24d252\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":\"ba7358dd-f666-48b1-b450-2948bb58ce4e\",\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_7201c9f6-9997-4fe2-a670-78a849868e71\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":\"command_e2ef835f-c6d1-49bd-8441-4fd73902ba05\",\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":\"cbf2f33e-001f-4ea6-9aec-6ee51479c334\",\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_0d25195e-d65d-46a9-8c02-1f79618e1073\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":null,\n" +
            "\"variableId\":\"1b87b2ce-7521-49bd-9f7f-b4444a4a08e4\",\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":{\n" +
            "\"arrayId\":\"e2435f4c-e3cc-4c88-a660-d05a859a53a0\",\n" +
            "\"index\":[\n" +
            "\"1b87b2ce-7521-49bd-9f7f-b4444a4a08e4\"\n" +
            "]\n" +
            "},\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_5266506e-63de-483b-a612-fa686610446d\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":\"35c0b2c7-5ad3-4adc-88c1-c173b366962a\",\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_e2ef835f-c6d1-49bd-8441-4fd73902ba05\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":{\n" +
            "\"id\":\"func\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall\",\n" +
            "\"arguments\":[\n" +
            "\"e2435f4c-e3cc-4c88-a660-d05a859a53a0\"\n" +
            "],\n" +
            "\"firstCommandId\":null\n" +
            "}\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_e81e418e-e6db-4d12-946c-7ff5871e52ef\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_0647e26a-eba3-4541-8f57-be50bc1aff52\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":\"5e4a2dd9-fca8-412b-bc79-4b24009b046a\",\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_5d6114eb-647a-44c4-af2c-f1d89cff61a4\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":\"9039b02c-bf99-432f-bc6f-b99dd8eb413e\",\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":{\n" +
            "\"arrayId\":\"deb58f77-7c43-4d2c-add6-75ed1deeeb62\",\n" +
            "\"index\":[\n" +
            "\"9039b02c-bf99-432f-bc6f-b99dd8eb413e\"\n" +
            "]\n" +
            "},\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_fd94bf64-b9fd-49e2-bf93-caeb302fb4ca\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":\"7548cff6-bb38-4689-a3fd-a0d31360dfef\",\n" +
            "\"constant\":null,\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_1dbef3d0-9adc-42a4-8e07-2be16b9d5709\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":\"91d9feae-d8c9-45e9-883e-96f7cfd5be52\",\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":{\n" +
            "\"arrayId\":\"deb58f77-7c43-4d2c-add6-75ed1deeeb62\",\n" +
            "\"index\":[\n" +
            "\"91d9feae-d8c9-45e9-883e-96f7cfd5be52\"\n" +
            "]\n" +
            "},\n" +
            "\"functionCall\":null\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"command_495c2d0e-b2aa-478c-99aa-5a6ab5639b73\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "\"nextId\":null,\n" +
            "\"ifBlocks\":null,\n" +
            "\"loops\":null,\n" +
            "\"operation\":null,\n" +
            "\"constant\":\"72b891c4-e84c-486e-b590-5bf33563e6c0\",\n" +
            "\"variableId\":null,\n" +
            "\"conditionId\":null,\n" +
            "\"nextDagTriggerIds\":null,\n" +
            "\"arrayCommand\":null,\n" +
            "\"functionCall\":null\n" +
            "}\n" +
            "],\n" +
            "\"ifBlocks\":[\n" +
            "],\n" +
            "\"operations\":[\n" +
            "{\n" +
            "\"id\":\"361df2e3-b00a-4a46-b54a-9d4771a1e5fb\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "\"operatorType\":\"=\",\n" +
            "\"operand1\":\"command_1c977a1e-5896-4d98-9636-9dafb1319308\",\n" +
            "\"operand2\":\"command_5bcb38c9-c6d7-4e4e-96ed-39d7aa24d252\"\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"cbf2f33e-001f-4ea6-9aec-6ee51479c334\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "\"operatorType\":\"=\",\n" +
            "\"operand1\":\"command_0d25195e-d65d-46a9-8c02-1f79618e1073\",\n" +
            "\"operand2\":\"command_5266506e-63de-483b-a612-fa686610446d\"\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"7548cff6-bb38-4689-a3fd-a0d31360dfef\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "\"operatorType\":\"+\",\n" +
            "\"operand1\":\"command_1dbef3d0-9adc-42a4-8e07-2be16b9d5709\",\n" +
            "\"operand2\":\"command_495c2d0e-b2aa-478c-99aa-5a6ab5639b73\"\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"5e4a2dd9-fca8-412b-bc79-4b24009b046a\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "\"operatorType\":\"=\",\n" +
            "\"operand1\":\"command_5d6114eb-647a-44c4-af2c-f1d89cff61a4\",\n" +
            "\"operand2\":\"command_fd94bf64-b9fd-49e2-bf93-caeb302fb4ca\"\n" +
            "}\n" +
            "],\n" +
            "\"conditions\":[\n" +
            "],\n" +
            "\"constants\":[\n" +
            "{\n" +
            "\"id\":\"ba7358dd-f666-48b1-b450-2948bb58ce4e\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "\"value\":1.0,\n" +
            "\"dataType\":\"Double\"\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"35c0b2c7-5ad3-4adc-88c1-c173b366962a\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "\"value\":1.0,\n" +
            "\"dataType\":\"Double\"\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"9039b02c-bf99-432f-bc6f-b99dd8eb413e\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "\"value\":1.0,\n" +
            "\"dataType\":\"Double\"\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"91d9feae-d8c9-45e9-883e-96f7cfd5be52\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "\"value\":1.0,\n" +
            "\"dataType\":\"Double\"\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"72b891c4-e84c-486e-b590-5bf33563e6c0\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "\"value\":2.0,\n" +
            "\"dataType\":\"Double\"\n" +
            "}\n" +
            "],\n" +
            "\"arrays\":[\n" +
            "{\n" +
            "\"id\":\"e2435f4c-e3cc-4c88-a660-d05a859a53a0\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array\",\n" +
            "\"name\":\"arr\",\n" +
            "\"dataType\":\"array\",\n" +
            "\"values\":{\n" +
            "}\n" +
            "},\n" +
            "{\n" +
            "\"id\":\"deb58f77-7c43-4d2c-add6-75ed1deeeb62\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array\",\n" +
            "\"name\":\"y\",\n" +
            "\"dataType\":\"array\",\n" +
            "\"values\":{\n" +
            "}\n" +
            "}\n" +
            "],\n" +
            "\"functionCalls\":[\n" +
            "{\n" +
            "\"id\":\"func\",\n" +
            "\"clazz\":\"in.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall\",\n" +
            "\"arguments\":[\n" +
            "\"deb58f77-7c43-4d2c-add6-75ed1deeeb62\"\n" +
            "],\n" +
            "\"firstCommandId\":\"command_0647e26a-eba3-4541-8f57-be50bc1aff52\"\n" +
            "}\n" +
            "]\n" +
            "}";

            public static String arrayInFunctionFirstCommandId = "command_753ab9b6-48ce-489d-891d-7c866c698a0a";
}

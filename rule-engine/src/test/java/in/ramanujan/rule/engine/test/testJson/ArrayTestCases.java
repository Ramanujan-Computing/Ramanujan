package in.ramanujan.rule.engine.test.testJson;

public class ArrayTestCases {
    public static String ioIn2DArray = "{\n" +
            "    \"variables\": [\n" +
            "        {\n" +
            "            \"id\": \"x\",\n" +
            "            \"name\": \"x\",\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"commands\": [\n" +
            "        {\n" +
            "            \"id\":\"com0\",\n" +
            "            \"nextId\":\"com1\",\n" +
            "            \"operation\":\"op0\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\":\"command1Op0\",\n" +
            "            \"variableId\":\"x\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com1\",\n" +
            "            \"nextId\": \"com2\",\n" +
            "            \"operation\": \"op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"command1Op1\",\n" +
            "            \"arrayCommand\": {\n" +
            "                \"arrayId\": \"arr1\",\n" +
            "                \"index\": [\n" +
            "                    \"x\",\n" +
            "                    \"constantId1\"\n" +
            "                ]\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"command2Op1\",\n" +
            "            \"constant\": \"constantId1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com2\",\n" +
            "            \"operation\": \"op2\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"command2Op2\",\n" +
            "            \"operation\": \"op3\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"arrays\":[\n" +
            "        {\n" +
            "            \"id\":\"arr1\",\n" +
            "               \"name\":\"arr1\",\n" +
            "            \"dataType\":\"Integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"ifBlocks\": [],\n" +
            "    \"operations\": [\n" +
            "        {\n" +
            "            \"id\":\"op0\",\n" +
            "            \"operatorType\":\"=\",\n" +
            "            \"operand1\":\"command1Op0\",\n" +
            "            \"operand2\":\"command2Op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"op1\",\n" +
            "            \"operatorType\": \"=\",\n" +
            "            \"operand1\": \"command1Op1\",\n" +
            "            \"operand2\": \"command2Op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"op2\",\n" +
            "            \"operatorType\": \"=\",\n" +
            "            \"operand1\": \"command1Op1\",\n" +
            "            \"operand2\": \"command2Op2\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"op3\",\n" +
            "            \"operatorType\": \"+\",\n" +
            "            \"operand1\": \"command1Op1\",\n" +
            "            \"operand2\": \"command2Op1\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"conditions\": [],\n" +
            "    \"constants\": [\n" +
            "        {\n" +
            "            \"id\": \"constantId1\",\n" +
            "            \"value\": 10,\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"functions\": []\n" +
            "}";

    public  static String firstCommand = "com0";


    public static String ruleEngineIfSomeVariablesAreSetFromOtherThread = "{\n" +
            "    \"variables\": [\n" +
            "        {\n" +
            "            \"id\": \"x\",\n" +
            "            \"name\": \"x\",\n" +
            "            \"dataType\": \"Integer\",\n" +
            "            \"value\": 10\n" +
            "        }\n" +
            "    ],\n" +
            "    \"commands\": [\n" +
            "        {\n" +
            "            \"id\": \"com91\",\n" +
            "            \"nextId\": \"com1\",\n" +
            "            \"operation\": \"op0\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"command1Op0\",\n" +
            "            \"variableId\": \"x\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com1\",\n" +
            "            \"nextId\": \"com2\",\n" +
            "            \"operation\": \"op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"command1Op1\",\n" +
            "            \"arrayCommand\": {\n" +
            "                \"arrayId\": \"arr1\",\n" +
            "                \"index\": [\n" +
            "                    \"x\",\n" +
            "                    \"constantId1\"\n" +
            "                ]\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"command2Op1\",\n" +
            "            \"constant\": \"constantId1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com0\",\n" +
            "            \"operation\": \"op2\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"command2Op2\",\n" +
            "            \"operation\": \"op3\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"arrays\": [\n" +
            "        {\n" +
            "            \"id\": \"arr1\",\n" +
            "            \"name\": \"arr1\",\n" +
            "            \"dataType\": \"Integer\",\n" +
            "            \"values\": {\n" +
            "                \"10_10\": 10\n" +
            "            }\n" +
            "        }\n" +
            "    ],\n" +
            "    \"ifBlocks\": [],\n" +
            "    \"operations\": [\n" +
            "        {\n" +
            "            \"id\": \"op0\",\n" +
            "            \"operatorType\": \"=\",\n" +
            "            \"operand1\": \"command1Op0\",\n" +
            "            \"operand2\": \"command2Op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"op1\",\n" +
            "            \"operatorType\": \"=\",\n" +
            "            \"operand1\": \"command1Op1\",\n" +
            "            \"operand2\": \"command2Op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"op2\",\n" +
            "            \"operatorType\": \"=\",\n" +
            "            \"operand1\": \"command1Op1\",\n" +
            "            \"operand2\": \"command2Op2\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"op3\",\n" +
            "            \"operatorType\": \"+\",\n" +
            "            \"operand1\": \"command1Op1\",\n" +
            "            \"operand2\": \"command2Op1\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"conditions\": [],\n" +
            "    \"constants\": [\n" +
            "        {\n" +
            "            \"id\": \"constantId1\",\n" +
            "            \"value\": 10,\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"functions\": []\n" +
            "}";
}

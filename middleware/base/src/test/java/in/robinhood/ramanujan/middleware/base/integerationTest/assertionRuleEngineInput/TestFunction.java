package in.robinhood.ramanujan.middleware.base.integerationTest.assertionRuleEngineInput;

public class TestFunction {
    public static String assertion = "{\n" +
            "    \"variables\": [\n" +
            "        {\n" +
            "            \"id\": \"y\",\n" +
            "            \"name\": \"y\",\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"x\",\n" +
            "            \"name\": \"x\",\n" +
            "            \"dataType\": \"Integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"commands\": [\n{\"id\":\"com0\",\"nextId\":\"com1\"},"+
            "        {\n" +
            "            \"id\":\"com1\",\n" +
            "            \"operation\":\"op3\",\n" +
            "            \"nextId\":\"com2\"\n" +
            "        },{\n" +
            "            \"id\":\"com2\",\n" +
            "            \"functionCall\":{\n" +
            "                \"id\":\"func1\",\n" +
            "                \"arguments\":[\"x\"]\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com1Func1\",\n" +
            "            \"operation\": \"op1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com1Op1\",\n" +
            "            \"variableId\": \"y\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com2Op1\",\n" +
            "            \"operation\": \"op2\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com1Op2\",\n" +
            "            \"variableId\": \"y\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"com2Op2\",\n" +
            "            \"constant\": \"constant1\"\n" +
            "        },{\n" +
            "            \"id\":\"com1Op3\",\n" +
            "            \"variableId\":\"x\"\n" +
            "        },{\n" +
            "            \"id\":\"com2Op3\",\n" +
            "            \"constant\":\"constant2\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"arrays\": [],\n" +
            "    \"ifBlocks\": [],\n" +
            "    \"operations\": [\n" +
            "        {\n" +
            "            \"id\": \"op1\",\n" +
            "            \"operand1\": \"com1Op1\",\n" +
            "            \"operand2\": \"com2Op1\",\n" +
            "            \"operatorType\":\"=\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"op2\",\n" +
            "            \"operand1\": \"com1Op2\",\n" +
            "            \"operand2\": \"com2Op2\",\n" +
            "            \"operatorType\":\"+\"\n" +
            "        },{\n" +
            "            \"id\":\"op3\",\n" +
            "            \"operand1\":\"com1Op3\",\n" +
            "            \"operand2\":\"com2Op3\",\n" +
            "            \"operatorType\":\"=\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"conditions\": [],\n" +
            "    \"constants\": [\n" +
            "        {\n" +
            "            \"id\": \"constant1\",\n" +
            "            \"value\": 2.0\n,\"dataType\":\"Double\"" +
            "        },{\n" +
            "            \"id\":\"constant2\",\n" +
            "            \"value\":1.0\n,\"dataType\":\"Double\"" +
            "        }\n" +
            "    ],\n" +
            "    \"functionCalls\": [\n" +
            "        {\n" +
            "            \"id\": \"func1\",\n" +
            "            \"firstCommandId\": \"com1Func1\",\n" +
            "            \"arguments\": [\n" +
            "                \"y\"\n" +
            "            ]\n" +
            "        }\n" +
            "    ]\n" +
            "}";
}

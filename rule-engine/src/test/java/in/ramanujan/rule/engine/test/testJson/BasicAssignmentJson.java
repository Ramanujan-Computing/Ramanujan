package in.ramanujan.rule.engine.test.testJson;

public class BasicAssignmentJson {
    public static String input = "{\n" +
            "    \"commands\": [\n" +
            "        {\n" +
            "            \"id\": \"cmd1\",\n" +
            "            \"operation\": \"opId1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"operandOp1Com1\",\n" +
            "            \"variableId\": \"var1\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"id\": \"operandOp1Com2\",\n" +
            "            \"constant\": \"const1\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"variables\": [\n" +
            "        {\n" +
            "            \"id\": \"var1\",\n" +
            "            \"name\": \"x\",\n" +
            "            \"dataType\": \"integer\"\n" +
            "        }\n" +
            "    ],\n" +
            "    \"constants\": [\n" +
            "        {\n" +
            "            \"id\": \"const1\",\n" +
            "            \"value\": 10\n" +
            "        }\n" +
            "    ],\n" +
            "    \"operations\": [\n" +
            "        {\n" +
            "            \"id\": \"opId1\",\n" +
            "            \"operatorType\": \"=\",\n" +
            "            \"operand1\": \"operandOp1Com1\",\n" +
            "            \"operand2\": \"operandOp1Com2\"\n" +
            "        }\n" +
            "    ]\n" +
            "}";

    public static String firstCommandId = "cmd1";
}

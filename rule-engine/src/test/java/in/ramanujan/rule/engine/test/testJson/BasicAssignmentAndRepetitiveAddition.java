package in.ramanujan.rule.engine.test.testJson;

public class BasicAssignmentAndRepetitiveAddition {
    public static String input = "{\n" +
            "       \"commands\": [\n" +
            "           {\n" +
            "               \"id\": \"cmd1\",\n" +
            "               \"operation\": \"opId1\",\n" +
            "               \"nextId\":\"cmd2\"\n" +
            "           },\n" +
            "           {\n" +
            "               \"id\":\"cmd2\",\n" +
            "               \"operation\":\"opId2\"\n" +
            "           },\n" +
            "           {\n" +
            "               \"id\":\"operandOp1Com1\",\n" +
            "               \"variableId\":\"var1\"\n" +
            "           }, {\n" +
            "               \"id\":\"operandOp1Com2\",\n" +
            "               \"constant\":\"const1\"\n" +
            "           },\n" +
            "           {\n" +
            "               \"id\":\"operandOp2Com1\",\n" +
            "               \"variableId\":\"var2\"\n" +
            "           }, {\n" +
            "               \"id\":\"operandOp2Com2\",\n" +
            "               \"operation\":\"opId3\"\n" +
            "           }, {\n" +
            "               \"id\":\"operandOp3Com1\",\n" +
            "               \"variableId\":\"var1\"\n" +
            "           }, {\n" +
            "               \"id\":\"operandOp3Com2\",\n" +
            "               \"operation\":\"opId4\"\n" +
            "           }, {\n" +
            "               \"id\":\"operandOp4Com1\",\n" +
            "               \"variableId\":\"var1\"\n" +
            "           }, {\n" +
            "               \"id\":\"operandOp4Com2\",\n" +
            "               \"variableId\":\"var1\"\n" +
            "           }\n" +
            "       ],\n" +
            "       \"variables\":[\n" +
            "           {\n" +
            "               \"id\":\"var1\",\n" +
            "               \"name\":\"x\",\n" +
            "               \"dataType\":\"integer\"\n" +
            "           }\n" +
            "           , {\n" +
            "               \"id\":\"var2\",\n" +
            "               \"name\":\"y\",\n" +
            "               \"dataType\":\"integer\"\n" +
            "           }\n" +
            "       ],\n" +
            "       \"constants\":[\n" +
            "           {\n" +
            "               \"id\":\"const1\",\n" +
            "               \"value\":10\n" +
            "           }\n" +
            "       ],\n" +
            "       \"operations\": [\n" +
            "           {\n" +
            "               \"id\":\"opId1\",\n" +
            "               \"operatorType\":\"=\",\n" +
            "               \"operand1\":\"operandOp1Com1\",\n" +
            "               \"operand2\":\"operandOp1Com2\"\n" +
            "           },{\n" +
            "               \"id\":\"opId2\",\n" +
            "               \"operatorType\":\"=\",\n" +
            "               \"operand1\":\"operandOp2Com1\",\n" +
            "               \"operand2\":\"operandOp2Com2\"\n" +
            "           }, {\n" +
            "               \"id\":\"opId3\",\n" +
            "               \"operatorType\":\"+\",\n" +
            "               \"operand1\":\"operandOp3Com1\",\n" +
            "               \"operand2\":\"operandOp3Com2\"\n" +
            "           }, {\n" +
            "               \"id\":\"opId4\",\n" +
            "               \"operatorType\":\"+\",\n" +
            "               \"operand1\":\"operandOp4Com1\",\n" +
            "               \"operand2\":\"operandOp4Com2\"\n" +
            "           }\n" +
            "       ]\n" +
            "   }\n";

    public static String firstCommandId = "cmd1";
}

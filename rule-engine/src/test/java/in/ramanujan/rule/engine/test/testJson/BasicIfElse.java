package in.ramanujan.rule.engine.test.testJson;

public class BasicIfElse {
    public static String inputForIfCommand = "{\n" +
            "      \"commands\": [\n" +
            "          {\n" +
            "              \"id\": \"cmd1\",\n" +
            "              \"operation\": \"opId1\",\n" +
            "              \"nextId\":\"cmd2\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp1Com1\",\n" +
            "              \"variableId\":\"var1\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp1Com2\",\n" +
            "              \"constant\":\"const1\"\n" +
            "          },\n" +
            "          {\n" +
            "              \"id\":\"cmd2\",\n" +
            "              \"ifBlocks\":\"if1\"\n" +
            "          },{\n" +
            "              \"id\":\"comparisionCommand1\",\n" +
            "              \"variableId\":\"var1\"\n" +
            "          }, {\n" +
            "              \"id\":\"comparisionCommand2\",\n" +
            "              \"constant\":\"constCompare\"\n" +
            "          }, {\n" +
            "              \"id\":\"ifCommand\",\n" +
            "              \"operation\":\"opId2\"\n" +
            "          }, {\n" +
            "              \"id\":\"elseCommand\",\n" +
            "              \"operation\":\"opId3\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp2Com1\",\n" +
            "              \"variableId\":\"var2\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp2Com2\",\n" +
            "              \"constant\":\"constIf\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp3Com1\",\n" +
            "              \"variableId\":\"var2\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp3Com2\",\n" +
            "              \"constant\":\"constElse\"\n" +
            "          }\n" +
            "      ],\n" +
            "      \"conditions\":[{\n" +
            "          \"id\":\"condition1\",\n" +
            "          \"conditionType\":\"<\",\n" +
            "          \"comparisionCommand1\":\"comparisionCommand1\",\n" +
            "          \"comparisionCommand2\":\"comparisionCommand2\"\n" +
            "      }],\n" +
            "      \"ifBlocks\":[\n" +
            "          {\n" +
            "              \"id\":\"if1\",\n" +
            "              \"conditionId\":\"condition1\",\n" +
            "              \"ifCommand\":\"ifCommand\",\n" +
            "              \"elseCommandId\":\"elseCommand\"\n" +
            "          }\n" +
            "      ],\n" +
            "      \"variables\":[\n" +
            "          {\n" +
            "              \"id\":\"var1\",\n" +
            "              \"name\":\"x\",\n" +
            "              \"dataType\":\"integer\"\n" +
            "          }\n" +
            "          , {\n" +
            "              \"id\":\"var2\",\n" +
            "               \"name\":\"y\",\n" +
            "              \"dataType\":\"integer\"\n" +
            "          }\n" +
            "      ],\n" +
            "      \"constants\":[\n" +
            "          {\n" +
            "              \"id\":\"const1\",\n" +
            "              \"value\":10\n" +
            "          },{\n" +
            "              \"id\":\"constIf\",\n" +
            "              \"value\":20\n" +
            "          }, {\n" +
            "              \"id\":\"constElse\",\n" +
            "              \"value\":30\n" +
            "          }, {\n" +
            "              \"id\":\"constCompare\",\n" +
            "              \"value\":12\n" +
            "          }\n" +
            "      ],\n" +
            "      \"operations\": [\n" +
            "          {\n" +
            "              \"id\":\"opId1\",\n" +
            "              \"operatorType\":\"=\",\n" +
            "              \"operand1\":\"operandOp1Com1\",\n" +
            "              \"operand2\":\"operandOp1Com2\"\n" +
            "          }, {\n" +
            "              \"id\":\"opId2\",\n" +
            "              \"operatorType\":\"=\",\n" +
            "              \"operand1\":\"operandOp2Com1\",\n" +
            "              \"operand2\":\"operandOp2Com2\"\n" +
            "          }, {\n" +
            "              \"id\":\"opId3\",\n" +
            "              \"operatorType\":\"=\",\n" +
            "              \"operand1\":\"operandOp3Com1\",\n" +
            "              \"operand2\":\"operandOp3Com2\"\n" +
            "          }\n" +
            "      ]\n" +
            "  }\n";

    public static String inputForElseCommand = "{\n" +
            "      \"commands\": [\n" +
            "          {\n" +
            "              \"id\": \"cmd1\",\n" +
            "              \"operation\": \"opId1\",\n" +
            "              \"nextId\":\"cmd2\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp1Com1\",\n" +
            "              \"variableId\":\"var1\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp1Com2\",\n" +
            "              \"constant\":\"const1\"\n" +
            "          },\n" +
            "          {\n" +
            "              \"id\":\"cmd2\",\n" +
            "              \"ifBlocks\":\"if1\"\n" +
            "          },{\n" +
            "              \"id\":\"comparisionCommand1\",\n" +
            "              \"variableId\":\"var1\"\n" +
            "          }, {\n" +
            "              \"id\":\"comparisionCommand2\",\n" +
            "              \"constant\":\"constCompare\"\n" +
            "          }, {\n" +
            "              \"id\":\"ifCommand\",\n" +
            "              \"operation\":\"opId2\"\n" +
            "          }, {\n" +
            "              \"id\":\"elseCommand\",\n" +
            "              \"operation\":\"opId3\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp2Com1\",\n" +
            "              \"variableId\":\"var2\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp2Com2\",\n" +
            "              \"constant\":\"constIf\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp3Com1\",\n" +
            "              \"variableId\":\"var2\"\n" +
            "          }, {\n" +
            "              \"id\":\"operandOp3Com2\",\n" +
            "              \"constant\":\"constElse\"\n" +
            "          }\n" +
            "      ],\n" +
            "      \"conditions\":[{\n" +
            "          \"id\":\"condition1\",\n" +
            "          \"conditionType\":\"<\",\n" +
            "          \"comparisionCommand1\":\"comparisionCommand1\",\n" +
            "          \"comparisionCommand2\":\"comparisionCommand2\"\n" +
            "      }],\n" +
            "      \"ifBlocks\":[\n" +
            "          {\n" +
            "              \"id\":\"if1\",\n" +
            "              \"conditionId\":\"condition1\",\n" +
            "              \"ifCommand\":\"ifCommand\",\n" +
            "              \"elseCommandId\":\"elseCommand\"\n" +
            "          }\n" +
            "      ],\n" +
            "      \"variables\":[\n" +
            "          {\n" +
            "              \"id\":\"var1\",\n" +
            "              \"name\":\"x\",\n" +
            "              \"dataType\":\"integer\"\n" +
            "          }\n" +
            "          , {\n" +
            "              \"id\":\"var2\",\n" +
            "               \"name\":\"y\",\n" +
            "              \"dataType\":\"integer\"\n" +
            "          }\n" +
            "      ],\n" +
            "      \"constants\":[\n" +
            "          {\n" +
            "              \"id\":\"const1\",\n" +
            "              \"value\":13\n" +
            "          },{\n" +
            "              \"id\":\"constIf\",\n" +
            "              \"value\":20\n" +
            "          }, {\n" +
            "              \"id\":\"constElse\",\n" +
            "              \"value\":30\n" +
            "          }, {\n" +
            "              \"id\":\"constCompare\",\n" +
            "              \"value\":12\n" +
            "          }\n" +
            "      ],\n" +
            "      \"operations\": [\n" +
            "          {\n" +
            "              \"id\":\"opId1\",\n" +
            "              \"operatorType\":\"=\",\n" +
            "              \"operand1\":\"operandOp1Com1\",\n" +
            "              \"operand2\":\"operandOp1Com2\"\n" +
            "          }, {\n" +
            "              \"id\":\"opId2\",\n" +
            "              \"operatorType\":\"=\",\n" +
            "              \"operand1\":\"operandOp2Com1\",\n" +
            "              \"operand2\":\"operandOp2Com2\"\n" +
            "          }, {\n" +
            "              \"id\":\"opId3\",\n" +
            "              \"operatorType\":\"=\",\n" +
            "              \"operand1\":\"operandOp3Com1\",\n" +
            "              \"operand2\":\"operandOp3Com2\"\n" +
            "          }\n" +
            "      ]\n" +
            "  }\n";

    public  static String firstCommand = "cmd1";
}

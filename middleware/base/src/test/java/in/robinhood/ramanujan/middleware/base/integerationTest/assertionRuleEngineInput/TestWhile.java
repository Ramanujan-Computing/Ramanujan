package in.robinhood.ramanujan.middleware.base.integerationTest.assertionRuleEngineInput;

public class TestWhile {
    public static String assertion = "{\n" +
            "        \"variables\": [\n" +
            "            {\n" +
            "                \"id\": \"7c2cbc89-f03a-4f90-b378-1e5e662752bf\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Variable\",\n" +
            "                \"name\": \"x\",\n" +
            "                \"dataType\": \"integer\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"commands\": [\n" +
            "            {\n" +
            "                \"id\": \"com1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": \"com2\",\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"com2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": \"com3\",\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": \"op1\",\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op1Com1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": \"7c2cbc89-f03a-4f90-b378-1e5e662752bf\",\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op1Com2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": \"788f9c4d-4ed5-4df4-b528-34decea6a2c9\",\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"com3\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": \"com4\",\n" +
            "                \"whileBlocks\": \"if_2bf6e466-171d-4077-8d10-864cf4c43bc1\",\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"command_43ff4764-042b-4936-859e-1c146d16502a\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": \"7c2cbc89-f03a-4f90-b378-1e5e662752bf\",\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"command_0711fb6c-40c2-45ba-94bd-5f5dcc34c29a\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": \"247df5df-64fb-4179-b190-893397b6e5dc\",\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"ifCom1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": \"op4\",\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op4Com1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": \"7c2cbc89-f03a-4f90-b378-1e5e662752bf\",\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op4Com2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": \"op5\",\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op5Com1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": \"7c2cbc89-f03a-4f90-b378-1e5e662752bf\",\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op5Com2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": \"98b83c5f-c8eb-4b5b-9f8a-62f5f9063053\",\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"ifCom2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": \"if_2bf6e466-171d-4077-8d10-864cf4c43bc1\",\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"com4\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": \"op2\",\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op2Com1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": \"7c2cbc89-f03a-4f90-b378-1e5e662752bf\",\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op2Com2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": \"op3\",\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op3Com1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": null,\n" +
            "                \"variableId\": \"7c2cbc89-f03a-4f90-b378-1e5e662752bf\",\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op3Com2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command\",\n" +
            "                \"nextId\": null,\n" +
            "                \"ifBlocks\": null,\n" +
            "                \"loops\": null,\n" +
            "                \"operation\": null,\n" +
            "                \"constant\": \"bdd63b07-439e-49cb-a6bd-571901d1d08c\",\n" +
            "                \"variableId\": null,\n" +
            "                \"conditionId\": null,\n" +
            "                \"nextDagTriggerIds\": null,\n" +
            "                \"arrayCommand\": null,\n" +
            "                \"functionCall\": null\n" +
            "            }\n" +
            "        ],\n" +
            "        \"whileBlocks\": [\n" +
            "            {\n" +
            "                \"id\": \"if_2bf6e466-171d-4077-8d10-864cf4c43bc1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.If\",\n" +
            "                \"conditionId\": \"5b554515-e835-4850-a6ad-743f21bd3e2a\",\n" +
            "                \"whileCommandId\": \"ifCom1\",\n" +
            "                \"elseCommandId\": null\n" +
            "            }\n" +
            "        ],\n" +
            "        \"operations\": [\n" +
            "            {\n" +
            "                \"id\": \"op1\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "                \"operatorType\": \"=\",\n" +
            "                \"operand1\": \"op1Com1\",\n" +
            "                \"operand2\": \"op1Com2\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op5\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "                \"operatorType\": \"+\",\n" +
            "                \"operand1\": \"op5Com1\",\n" +
            "                \"operand2\": \"op5Com2\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op4\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "                \"operatorType\": \"=\",\n" +
            "                \"operand1\": \"op4Com1\",\n" +
            "                \"operand2\": \"op4Com2\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op3\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "                \"operatorType\": \"+\",\n" +
            "                \"operand1\": \"op3Com1\",\n" +
            "                \"operand2\": \"op3Com2\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"op2\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Operation\",\n" +
            "                \"operatorType\": \"=\",\n" +
            "                \"operand1\": \"op2Com1\",\n" +
            "                \"operand2\": \"op2Com2\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"conditions\": [\n" +
            "            {\n" +
            "                \"id\": \"5b554515-e835-4850-a6ad-743f21bd3e2a\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Condition\",\n" +
            "                \"conditionType\": \"<\",\n" +
            "                \"comparisionCommand1\": \"command_43ff4764-042b-4936-859e-1c146d16502a\",\n" +
            "                \"comparisionCommand2\": \"command_0711fb6c-40c2-45ba-94bd-5f5dcc34c29a\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"constants\": [\n" +
            "            {\n" +
            "                \"id\": \"788f9c4d-4ed5-4df4-b528-34decea6a2c9\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "                \"value\": 1.0,\n" +
            "                \"dataType\": \"Double\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"247df5df-64fb-4179-b190-893397b6e5dc\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "                \"value\": 10.0,\n" +
            "                \"dataType\": \"Double\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"98b83c5f-c8eb-4b5b-9f8a-62f5f9063053\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "                \"value\": 1.0,\n" +
            "                \"dataType\": \"Double\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"id\": \"bdd63b07-439e-49cb-a6bd-571901d1d08c\",\n" +
            "                \"clazz\": \"in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Constant\",\n" +
            "                \"value\": 10.0,\n" +
            "                \"dataType\": \"Double\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"arrays\": [],\n" +
            "        \"functionCalls\": []\n" +
            "    }";
}

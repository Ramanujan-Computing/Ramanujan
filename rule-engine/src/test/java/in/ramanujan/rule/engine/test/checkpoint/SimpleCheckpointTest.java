package in.ramanujan.rule.engine.test.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.CheckpointProcessor;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.test.testJson.*;
import in.ramanujan.utils.Constants;
import org.junit.Assert;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4ClassRunner.class)
public class SimpleCheckpointTest {
    //https://docs.google.com/document/d/13o13IuE6q4CJtX0O3oKkxL2WFaSNleD2WOkFhyR9BnE/edit

    private ObjectMapper objectMapper = new ObjectMapper();

    private Boolean assertMapObject(Map<String, Object> map1, Map<String, Object> map2) {
        if(map1 != null) {
            if(map2 == null) {
                return false;
            }
            for(String str : map1.keySet()) {
                if(map2.get(str) == null) {
                    return false;
                }
            }
        }
        if(map2 != null) {
            if(map1 == null) {
                return false;
            }
            for(String str : map2.keySet()) {
                if(map1.get(str) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    protected void assertForCheckpoint(Processor processor, Map<String, Object> result) {
        Assert.assertEquals(processor.getCheckpoint().getSize(), 0);
        Assert.assertEquals(processor.getCheckpoint().getCommandStack().size(), 0);
        Assert.assertTrue(assertMapObject(result, (Map<String, Object>)processor.getCheckpoint().getData()));
    }

    protected Checkpoint createCheckpoint(RuleEngineInput ruleEngineInput, String firstCommandId) {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.addStack(firstCommandId, new ContextStack());
        return checkpoint;
    }

    protected Checkpoint createCheckpoint(RuleEngineInput ruleEngineInput, List<String> commandIds,
                                          Map<String, Object> data, ContextStack contextStack) {
        Checkpoint checkpoint = new Checkpoint();
        for(String commandId : commandIds) {
            checkpoint.addStack(commandId, contextStack != null ? contextStack : new ContextStack());
        }
        checkpoint.setData(data);
        return checkpoint;
    }


    @Test
    public void basicAssignmentTest() throws Exception {
        String str = BasicAssignmentJson.input.replaceAll("\\n","");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                BasicAssignmentJson.firstCommandId), null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var1"), 10);
        assertForCheckpoint(processor, result);
    }

    @Test
    public void basicAssignmentAndAdditionOnThatVariableTest() throws  Exception {
        String str = BasicAssignmentAndAddToThatVariable.input.replaceAll("\\n","");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                BasicAssignmentAndAddToThatVariable.firstCommandId), null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 20.0);
        Assert.assertEquals(result.get("var1"), 10);
        assertForCheckpoint(processor, result);
    }

    @Test
    public void basicAssignmentAndRepetitiveAdditionTest() throws  Exception {
        String str = BasicAssignmentAndRepetitiveAddition.input.replaceAll("\\n","");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                BasicAssignmentAndRepetitiveAddition.firstCommandId), null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 30.0);
        Assert.assertEquals(result.get("var1"), 10);
        Assert.assertEquals(processor.getCheckpoint().getSize(), 0);
        assertForCheckpoint(processor, result);
    }

    @Test
    public void basicIfTest() throws Exception {
        String str = BasicIfElse.inputForIfCommand.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                BasicIfElse.firstCommand), null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 20);
        Assert.assertEquals(result.get("var1"), 10);
        assertForCheckpoint(processor, result);
    }



    @Test
    public void basicElseTest() throws Exception {
        String str = BasicIfElse.inputForElseCommand.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                BasicIfElse.firstCommand), null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 30);
        Assert.assertEquals(result.get("var1"), 13);
        assertForCheckpoint(processor, result);

    }



    @Test
    public void twoDimensionalArrayIOTest() throws Exception {
        String str = ArrayTestCases.ioIn2DArray.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                ArrayTestCases.firstCommand), null);
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex)).get("arr1"))).get("10_10") == 20d);
        assertForCheckpoint(processor, result);

    }
    @Test
    public void functionJsonTest() throws Exception {
        String str = FunctionJson.basicTestInput.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                FunctionJson.basicTestFirstCommandId), null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("x"), 20);
        Assert.assertEquals(result.get("y"), 20);
        Assert.assertEquals(result.get("z"), 20);
        assertForCheckpoint(processor, result);
    }

    @Test
    public void arrayInFunctionTest() throws Exception {
        String str = FunctionJson.arrayInFunctionTest.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        Processor processor = new CheckpointProcessor(ruleEngineInput, createCheckpoint(ruleEngineInput,
                FunctionJson.arrayInFunctionFirstCommandId), null);
        Map<String, Object> result = processor.process();

        Map<String, Map<String, Object>> arrayIndexOp = (Map) result.get(Constants.arrayIndex);
        for(String arrayId : arrayIndexOp.keySet()) {
            Assert.assertTrue((Double) arrayIndexOp.get(arrayId).get("1") == 3d);
        }
        assertForCheckpoint(processor, result);
    }


    /*
    * {var1}={10};{var2}={{var1}+{var1}}
    * checkpoint till {var1}+{var1}
     * */

    @Test
    public void checkpointIntermediateOperationTest1() throws Exception {
        String str = BasicAssignmentAndAddToThatVariable.input.replaceAll("\\n","");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        ContextStack contextStack = new ContextStack();
        List<String> commands = new ArrayList<String>() {{
            add("cmd2");
            add("operandOp2Com2");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("var1", (Integer) 10);
        }};

        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
//        Assert.assertEquals(result.get("var1"), 10);
        Assert.assertEquals(result.get("var2"), 20d);
        assertForCheckpoint(processor, result);
    }

    /*
     * {var1}={10};{var2}={{var1}+{{var1}+{var1}}}
     * checkpoint till {var1}+{var1}
     * */


    @Test
    public void checkpointIntermediateOperationTest2() throws Exception {
        String str = BasicAssignmentAndRepetitiveAddition.input.replaceAll("\\n","");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);
        ContextStack contextStack = new ContextStack();
        List<String> commands = new ArrayList<String>() {{
            add("cmd2");
            add("operandOp2Com2");
            add("operandOp3Com2");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("var1", (Integer) 10);
        }};

        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
//        Assert.assertEquals(result.get("var1"), 10);
        Assert.assertEquals(result.get("var2"), 30d);
        assertForCheckpoint(processor, result);
    }

}

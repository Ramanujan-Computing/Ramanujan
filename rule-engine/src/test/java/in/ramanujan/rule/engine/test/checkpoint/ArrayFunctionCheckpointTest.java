package in.ramanujan.rule.engine.test.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.CheckpointProcessor;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.test.testJson.FunctionJson;
import in.ramanujan.utils.Constants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Test different scenerios for the task that contains both function and array
 * */

@RunWith(JUnit4ClassRunner.class)
public class ArrayFunctionCheckpointTest extends SimpleCheckpointTest {
    private ObjectMapper objectMapper = new ObjectMapper();
    private String codeString = FunctionJson.arrayInFunctionTest.replaceAll("\\n", "");
    private RuleEngineInput ruleEngineInput;

    @Before
    public void init() throws Exception {
        ruleEngineInput = objectMapper.readValue(codeString, RuleEngineInput.class);
    }

    /*Checkpoint till y[1] = y[1] + 2*/
    @Test
    public void checkpointFirstCommandInsideFirstFunction() throws Exception {
        ContextStack contextStack = new ContextStack();
        contextStack.push(
                new HashMap<String, String>() {{
                    put("deb58f77-7c43-4d2c-add6-75ed1deeeb62", "e2435f4c-e3cc-4c88-a660-d05a859a53a0");
                }}
        );

        List<String> commands = new ArrayList<String>() {{
            add("command_e2ef835f-c6d1-49bd-8441-4fd73902ba05");
            add("command_0647e26a-eba3-4541-8f57-be50bc1aff52");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("1b87b2ce-7521-49bd-9f7f-b4444a4a08e4", (Double) 1.0);
            put(Constants.arrayIndex, new HashMap<String, Map<String, Object>>() {{
                put("deb58f77-7c43-4d2c-add6-75ed1deeeb62", new HashMap<String, Object>() {{
                    put("1", (Double) 1.0);
                }});
                put("e2435f4c-e3cc-4c88-a660-d05a859a53a0", new HashMap<String, Object>() {
                    {
                        put("1", (Double) 1.0);
                    }
                });
            }});
        }};


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
//        Assert.assertTrue((Double) result.get("1b87b2ce-7521-49bd-9f7f-b4444a4a08e4") ==  1d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("e2435f4c-e3cc-4c88-a660-d05a859a53a0"))).get("1") == 3d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("deb58f77-7c43-4d2c-add6-75ed1deeeb62"))).get("1") == 3d);
        assertForCheckpoint(processor, result);
    }

    /*Checkpoint till: exec func(arr)*/
    @Test
    public void checkpointExecFunction() throws Exception {
        ContextStack contextStack = new ContextStack();

        List<String> commands = new ArrayList<String>() {{
            add("command_e2ef835f-c6d1-49bd-8441-4fd73902ba05");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("1b87b2ce-7521-49bd-9f7f-b4444a4a08e4", (Double) 1.0);
            put(Constants.arrayIndex, new HashMap<String, Map<String, Object>>() {{
                put("e2435f4c-e3cc-4c88-a660-d05a859a53a0", new HashMap<String, Object>() {
                    {
                        put("1", (Double) 1.0);
                    }
                });
            }});
        }};


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
        //Assert.assertTrue((Double) result.get("1b87b2ce-7521-49bd-9f7f-b4444a4a08e4") ==  1d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("e2435f4c-e3cc-4c88-a660-d05a859a53a0"))).get("1") == 3d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("deb58f77-7c43-4d2c-add6-75ed1deeeb62"))).get("1") == 3d);
        assertForCheckpoint(processor, result);
    }

    /*Checkpoint till {x}={1};*/
    @Test
    public void checkpointFirstCommand() throws Exception {
        ContextStack contextStack = new ContextStack();

        List<String> commands = new ArrayList<String>() {{
            add("command_43abe5ed-1524-4790-9bfa-a03840cd7d8c");
        }};

        Map<String, Object> data = new HashMap<String, Object>();


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
        Assert.assertTrue((Double) result.get("1b87b2ce-7521-49bd-9f7f-b4444a4a08e4") ==  1d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("e2435f4c-e3cc-4c88-a660-d05a859a53a0"))).get("1") == 3d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("deb58f77-7c43-4d2c-add6-75ed1deeeb62"))).get("1") == 3d);
        assertForCheckpoint(processor, result);
    }

    /*Checkpoint till {arr[x]}={1};*/
    @Test
    public void checkpointArrayAssignCommand() throws Exception {
        ContextStack contextStack = new ContextStack();

        List<String> commands = new ArrayList<String>() {{
            add("command_7201c9f6-9997-4fe2-a670-78a849868e71");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("1b87b2ce-7521-49bd-9f7f-b4444a4a08e4", (Double) 1.0);
        }};


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
//        Assert.assertTrue((Double) result.get("1b87b2ce-7521-49bd-9f7f-b4444a4a08e4") ==  1d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("e2435f4c-e3cc-4c88-a660-d05a859a53a0"))).get("1") == 3d);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex))
                .get("deb58f77-7c43-4d2c-add6-75ed1deeeb62"))).get("1") == 3d);
        assertForCheckpoint(processor, result);
    }
}

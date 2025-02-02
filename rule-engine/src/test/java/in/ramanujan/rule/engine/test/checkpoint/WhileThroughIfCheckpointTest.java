package in.ramanujan.rule.engine.test.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.CheckpointProcessor;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.test.testJson.WhileThroughIfTestCases;
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
* Test different scenerios when checkpoint is there in the while loop
* */

@RunWith(JUnit4ClassRunner.class)
public class WhileThroughIfCheckpointTest extends SimpleCheckpointTest {
    private ObjectMapper objectMapper = new ObjectMapper();
    private String codeString = WhileThroughIfTestCases.ruleEngineUnit.replaceAll("\\n", "");
    private RuleEngineInput ruleEngineInput;

    @Before
    public void init() throws Exception {
        ruleEngineInput = objectMapper.readValue(codeString, RuleEngineInput.class);
    }

    /*Checkpoint till first iteration: before while loop*/
    @Test
    public void testCheckpointTillFirstIter() {
        ContextStack contextStack = new ContextStack();

        List<String> commands = new ArrayList<String>() {{
            add("com3");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("7c2cbc89-f03a-4f90-b378-1e5e662752bf", (Double) 1.0);
        }};


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertTrue((Double) result.get("7c2cbc89-f03a-4f90-b378-1e5e662752bf") == 20d);
        assertForCheckpoint(processor, result);
    }

    /*Checkpoint till {x}={{x}+{1}}; in the third iteration*/
    @Test
    public void testCheckpointTillAssignInThirdIteration() {
        ContextStack contextStack = new ContextStack();

        List<String> commands = new ArrayList<String>() {{
            add("com3");
            add("ifCom2");
            add("ifCom2");
            add("ifCom1");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("7c2cbc89-f03a-4f90-b378-1e5e662752bf", (Double) 3.0);
        }};


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertTrue((Double) result.get("7c2cbc89-f03a-4f90-b378-1e5e662752bf") == 20d);
        assertForCheckpoint(processor, result);
    }


    /*Checkpoint till {x}={{x}+{1}}; in the ninth (last) iteration*/
    @Test
    public void testCheckpointTillAssignInLastIteration() {
        ContextStack contextStack = new ContextStack();

        List<String> commands = new ArrayList<String>() {{
            add("com3");
        }};

        for(int i = 0; i < 9;i++) {
            commands.add("ifCom2");
        }
        commands.add("ifCom1");

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("7c2cbc89-f03a-4f90-b378-1e5e662752bf", (Double) 9.0);
        }};


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertTrue((Double) result.get("7c2cbc89-f03a-4f90-b378-1e5e662752bf") == 20d);
        assertForCheckpoint(processor, result);
    }

    /*Checkpoint till {x}={{x}+{10}};*/
    @Test
    public void testCheckpointTillLastIteration() {
        ContextStack contextStack = new ContextStack();

        List<String> commands = new ArrayList<String>() {{
            add("com4");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("7c2cbc89-f03a-4f90-b378-1e5e662752bf", (Double) 10.0);
        }};


        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertTrue((Double) result.get("7c2cbc89-f03a-4f90-b378-1e5e662752bf") == 20d);
        assertForCheckpoint(processor, result);
    }
}

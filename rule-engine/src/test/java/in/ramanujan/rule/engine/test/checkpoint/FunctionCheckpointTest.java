package in.ramanujan.rule.engine.test.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.CheckpointProcessor;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.test.testJson.FunctionJson;
import org.junit.Assert;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4ClassRunner.class)
public class FunctionCheckpointTest extends SimpleCheckpointTest {
    //https://docs.google.com/document/d/13o13IuE6q4CJtX0O3oKkxL2WFaSNleD2WOkFhyR9BnE/edit
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void CheckpointInCallOfFirstFunctionTest() throws Exception {
        String str = FunctionJson.basicTestInput.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("x", 10);
        }};

        List<String> commandStack = new ArrayList<String>() {{
            add("com2");
            add("comm3");
        }};

        ContextStack contextStack = new ContextStack();
        contextStack.push(new HashMap<String, String>() {{
            put("y", "x");
        }});

        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commandStack, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("x"), 20);
        Assert.assertEquals(result.get("y"), 20);
        Assert.assertEquals(result.get("z"), 20);
        assertForCheckpoint(processor, result);
    }

    @Test
    public void CheckpointInCallOfSecondFunctionTest() throws Exception {
        String str = FunctionJson.basicTestInput.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("x", 10);
            put("y", 10);
            put("z", 10);
        }};

        List<String> commandStack = new ArrayList<String>() {{
            add("com2");
            add("comm3");
            add("comm4");
        }};

        ContextStack contextStack = new ContextStack();
        contextStack.push(new HashMap<String, String>() {{
            put("y", "x");
        }});
        contextStack.push(new HashMap<String, String>() {{
            put("y", "x");
            put("z","y");
        }});

        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commandStack, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("x"), 20);
        Assert.assertEquals(result.get("y"), 20);
        Assert.assertEquals(result.get("z"), 20);
        assertForCheckpoint(processor, result);
    }


    @Test
    public void CheckpointInCallOfSecondFunctionNegativeTest() throws Exception {
        String str = FunctionJson.basicTestInput.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("x", 10);
            put("y", 10);
            put("z", 10);
        }};

        List<String> commandStack = new ArrayList<String>() {{
            add("com2");
            add("comm3");
            add("comm4");
        }};

        ContextStack contextStack = new ContextStack();
        contextStack.push(new HashMap<String, String>() {{
            put("y", "x");
        }});

        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commandStack, data, contextStack);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
//        Assert.assertEquals(result.get("x"), 10);
//        Assert.assertEquals(result.get("y"), 10);
        Assert.assertEquals(result.get("z"), 20);
        assertForCheckpoint(processor, result);
    }
}

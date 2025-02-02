package in.ramanujan.rule.engine.test.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.CheckpointProcessor;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.test.testJson.ArrayTestCases;
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
public class ArrayCheckpointTest extends SimpleCheckpointTest {
    //https://docs.google.com/document/d/13o13IuE6q4CJtX0O3oKkxL2WFaSNleD2WOkFhyR9BnE/edit
    private ObjectMapper objectMapper = new ObjectMapper();
    @Test
    public void twoDimensionalArrayIOCheckpointInAssignmentTest() throws Exception {
        String str = ArrayTestCases.ioIn2DArray.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);

        List<String> commands = new ArrayList<String>() {{
            add("com2");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
           put("x", 10);
           put(Constants.arrayIndex, new HashMap<String, Map<String, Object>>() {{
               put("arr1", new HashMap<String, Object>() {{
                   put("10_10", 10);
               }});

           }});
        }};

        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, null);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex)).get("arr1"))).get("10_10") == 20d);
        assertForCheckpoint(processor, result);

    }

    @Test
    public void twoDimensionalArrayIOCheckpointInAdditionTest() throws Exception {
        String str = ArrayTestCases.ioIn2DArray.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);

        List<String> commands = new ArrayList<String>() {{
            add("com2");
            add("command2Op2");
        }};

        Map<String, Object> data = new HashMap<String, Object>() {{
            put("x", 10);
            put(Constants.arrayIndex, new HashMap<String, Map<String, Object>>() {{
                put("arr1", new HashMap<String, Object>() {{
                    put("10_10", 10);
                }});

            }});
        }};

        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commands, data, null);
        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex)).get("arr1"))).get("10_10") == 20d);
        assertForCheckpoint(processor, result);

    }
}

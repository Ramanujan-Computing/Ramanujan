package in.ramanujan.rule.engine.test.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.CheckpointProcessor;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.test.testJson.BasicIfElse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4ClassRunner.class)
public class IfElseBlockCheckpointTest extends SimpleCheckpointTest {

    //https://docs.google.com/document/d/13o13IuE6q4CJtX0O3oKkxL2WFaSNleD2WOkFhyR9BnE/edit

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void basicIfTestWithCheckpointInIfBlock() throws Exception {
        String str = BasicIfElse.inputForIfCommand.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);

        List<String> commandStack = new ArrayList<String>(){{
            add("cmd2");
            add("ifCommand");
        }};
        Map<String, Object> data = new HashMap<String, Object>(){{
            put("var1", 10);
        }};
        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commandStack, data, null);

        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 20);
//        Assert.assertEquals(result.get("var1"), 10);
        assertForCheckpoint(processor, result);
    }

    @Test
    public void basicElseTestWithCheckpointInElsePart() throws Exception {
        String str = BasicIfElse.inputForElseCommand.replaceAll("\\n", "");
        RuleEngineInput ruleEngineInput = objectMapper.readValue(str, RuleEngineInput.class);

        List<String> commandStack = new ArrayList<String>(){{
            add("cmd2");
            add("elseCommand");
        }};
        Map<String, Object> data = new HashMap<String, Object>(){{
            put("var1", 13);
        }};
        Checkpoint checkpoint = createCheckpoint(ruleEngineInput, commandStack, data, null);

        Processor processor = new CheckpointProcessor(ruleEngineInput, checkpoint, null);
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 30);
//        Assert.assertEquals(result.get("var1"), 13);
        assertForCheckpoint(processor, result);

    }
}

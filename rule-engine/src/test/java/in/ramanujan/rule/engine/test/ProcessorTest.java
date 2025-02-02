package in.ramanujan.rule.engine.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.rule.engine.Processor;
import in.ramanujan.rule.engine.test.testJson.*;
import in.ramanujan.utils.Constants;
import org.junit.Assert;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Map;

@RunWith(JUnit4ClassRunner.class)
public class ProcessorTest {

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

    private void assertForCheckpoint(Processor processor, Map<String, Object> result) {
//        Assert.assertEquals(processor.getCheckpoint().getSize(), 0);
//        Assert.assertEquals(processor.getCheckpoint().getCommandStack().size(), 0);
//        Assert.assertTrue(assertMapObject(result, (Map<String, Object>)processor.getCheckpoint().getData()));
    }

    @Test
    public void basicAssignmentTest() throws Exception {
        String str = BasicAssignmentJson.input.replaceAll("\\n","");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class),
                BasicAssignmentJson.firstCommandId, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var1"), 10);
        assertForCheckpoint(processor, result);
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        //here var1 ID correspond to variable x. It puts 10 in x.
//        Assert.assertEquals(debugPointList.size(), 1);
//        Map<String, String> beforeOp = debugPointList.get(0).getBeforeValue();
//        Map<String, String> afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertTrue(beforeOp.get("x") == null);
//        Assert.assertEquals(afterOp.get("x"), "10");
    }

    @Test
    public void basicAssignmentAndAdditionOnThatVariableTest() throws  Exception {
        String str = BasicAssignmentAndAddToThatVariable.input.replaceAll("\\n","");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class),
                BasicAssignmentAndAddToThatVariable.firstCommandId, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 20.0);
        Assert.assertEquals(result.get("var1"), 10);
        assertForCheckpoint(processor, result);
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        //here var1 ID correspond to variable x. var2 ID corresponds to variable y.
//        Assert.assertEquals(debugPointList.size(), 2);
//        Map<String, String> beforeOp = debugPointList.get(0).getBeforeValue();
//        Map<String, String> afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertTrue(beforeOp.get("x") == null);
//        Assert.assertEquals(afterOp.get("x"), "10");
//
//        beforeOp = debugPointList.get(1).getBeforeValue();
//        afterOp = debugPointList.get(1).getAfterValue();
//        Assert.assertTrue(beforeOp.get("y") == null);
//        Assert.assertEquals(afterOp.get("y"), "20.0");
    }

    @Test
    public void basicAssignmentAndRepetitiveAdditionTest() throws  Exception {
        String str = BasicAssignmentAndRepetitiveAddition.input.replaceAll("\\n","");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class),
                BasicAssignmentAndRepetitiveAddition.firstCommandId, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 30.0);
        Assert.assertEquals(result.get("var1"), 10);
        Assert.assertEquals(processor.getCheckpoint().getSize(), 0);
        assertForCheckpoint(processor, result);
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        //here var1 ID correspond to variable x. var2 ID corresponds to variable y.
//        Assert.assertEquals(debugPointList.size(), 2);
//        Map<String, String> beforeOp = debugPointList.get(0).getBeforeValue();
//        Map<String, String> afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertTrue(beforeOp.get("x") == null);
//        Assert.assertEquals(afterOp.get("x"), "10");
//
//        beforeOp = debugPointList.get(1).getBeforeValue();
//        afterOp = debugPointList.get(1).getAfterValue();
//        Assert.assertTrue(beforeOp.get("y") == null);
//        Assert.assertEquals(afterOp.get("y"), "30.0");
    }

    @Test
    public void basicIfTest() throws Exception {
        String str = BasicIfElse.inputForIfCommand.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class), BasicIfElse.firstCommand, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 20);
        Assert.assertEquals(result.get("var1"), 10);
        assertForCheckpoint(processor, result);
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        //here var1 ID correspond to variable x. var2 ID corresponds to variable y.
//        Assert.assertEquals(debugPointList.size(), 3);
//        Map<String, String> beforeOp = debugPointList.get(0).getBeforeValue();
//        Map<String, String> afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertTrue(beforeOp.get("x") == null);
//        Assert.assertEquals(afterOp.get("x"), "10");
//
//
//        //if-part
//        beforeOp = debugPointList.get(1).getBeforeValue();
//        Boolean condition = debugPointList.get(1).getConditionVal();
//        Assert.assertEquals(condition, true);
//
//        beforeOp = debugPointList.get(2).getBeforeValue();
//        afterOp = debugPointList.get(2).getAfterValue();
//        Assert.assertTrue(beforeOp.get("y") == null);
//        Assert.assertEquals(afterOp.get("y"), "20");
    }

    @Test
    public void basicElseTest() throws Exception {
        String str = BasicIfElse.inputForElseCommand.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class), BasicIfElse.firstCommand, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("var2"), 30);
        Assert.assertEquals(result.get("var1"), 13);
        assertForCheckpoint(processor, result);
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        //here var1 ID correspond to variable x. var2 ID corresponds to variable y.
//        Assert.assertEquals(debugPointList.size(), 3);
//        Map<String, String> beforeOp = debugPointList.get(0).getBeforeValue();
//        Map<String, String> afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertTrue(beforeOp.get("x") == null);
//        Assert.assertEquals(afterOp.get("x"), "13");
//
//        Assert.assertEquals(debugPointList.get(1).getConditionVal(), false);
//
//        beforeOp = debugPointList.get(2).getBeforeValue();
//        afterOp = debugPointList.get(2).getAfterValue();
//        Assert.assertTrue(beforeOp.get("y") == null);
//        Assert.assertEquals(afterOp.get("y"), "30");

    }

    @Test
    public void twoDimensionalArrayIOTest() throws Exception {
        String str = ArrayTestCases.ioIn2DArray.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class), ArrayTestCases.firstCommand, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex)).get("arr1"))).get("10_10") == 20d);
        assertForCheckpoint(processor, result);
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//       //x=10; arr[x][10]=10; arr[x][10]=arr[x][10]+10;
//        Assert.assertEquals(debugPointList.size(), 3);
//        Map<String, String> beforeOp = debugPointList.get(0).getBeforeValue();
//        Map<String, String> afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertTrue(beforeOp.get("x") == null);
//        Assert.assertEquals(afterOp.get("x"), "10");
//
//        beforeOp = debugPointList.get(1).getBeforeValue();
//        afterOp = debugPointList.get(1).getAfterValue();
//        Assert.assertTrue(beforeOp.get("arr1[10_10]") == null);
//        Assert.assertEquals(beforeOp.get("x"), "10");
//        Assert.assertEquals(afterOp.get("arr1[10_10]"), "10");
//
//        beforeOp = debugPointList.get(2).getBeforeValue();
//        afterOp = debugPointList.get(2).getAfterValue();
//        Assert.assertEquals(beforeOp.get("arr1[10_10]"), "10");
//        Assert.assertEquals(beforeOp.get("x"), "10");
//        Assert.assertEquals(afterOp.get("arr1[10_10]"), "20.0");

    }
    @Test
    public void functionJsonTest() throws Exception {
        String str = FunctionJson.basicTestInput.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class), FunctionJson.basicTestFirstCommandId, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertEquals(result.get("x"), 20);
        Assert.assertEquals(result.get("y"), 20);
        Assert.assertEquals(result.get("z"), 20);
        assertForCheckpoint(processor, result);
        //x=10; func1(x) -> y=x; func(y) -> z=y; z=20;
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        Assert.assertEquals(debugPointList.size(), 4);
//        Map<String, String> beforeOp, afterOp, funcArgMap;
//
//        beforeOp = debugPointList.get(0).getBeforeValue();
//        afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertEquals(beforeOp.get("x"), null);
//        Assert.assertEquals(afterOp.get("x"), "10");
//
//        beforeOp = debugPointList.get(1).getBeforeValue();
//        afterOp = debugPointList.get(1).getAfterValue();
//        funcArgMap = debugPointList.get(1).getFunctionArgCallArgMap();
//        Assert.assertEquals(beforeOp.keySet().size(), 0);
//        Assert.assertEquals(afterOp.get("y"), "10");
//        Assert.assertEquals(funcArgMap.get("x"), "y");
//
//        beforeOp = debugPointList.get(2).getBeforeValue();
//        afterOp = debugPointList.get(2).getAfterValue();
//        funcArgMap = debugPointList.get(2).getFunctionArgCallArgMap();
//        Assert.assertEquals(beforeOp.keySet().size(), 0);
//        Assert.assertEquals(afterOp.get("z"), "10");
//        Assert.assertEquals(funcArgMap.get("y"), "z");
//
//        beforeOp = debugPointList.get(3).getBeforeValue();
//        afterOp = debugPointList.get(3).getAfterValue();
//        Assert.assertEquals(beforeOp.get("z"), "10");
//        Assert.assertEquals(afterOp.get("z"), "20");
    }

    @Test
    public void arrayInFunctionTest() throws Exception {
        String str = FunctionJson.arrayInFunctionTest.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class), FunctionJson.arrayInFunctionFirstCommandId, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();

        Map<String, Map<String, Object>> arrayIndexOp = (Map) result.get(Constants.arrayIndex);
        for(String arrayId : arrayIndexOp.keySet()) {
            Assert.assertTrue((Double) arrayIndexOp.get(arrayId).get("1") == 3d);
        }
        assertForCheckpoint(processor, result);
        //def func(var y:array){{y[1]}={{y[1]}+{2}};}var x:integer;var arr:array;{x}={1};{arr[x]}={1};exec func(arr);

//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        Assert.assertEquals(debugPointList.size(), 4);
//        Map<String, String> beforeOp, afterOp, funcArgMap;
//
//        beforeOp = debugPointList.get(0).getBeforeValue();
//        afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertEquals(beforeOp.get("x"), null);
//        Assert.assertEquals(afterOp.get("x"), "1.0");
//
//        beforeOp = debugPointList.get(1).getBeforeValue();
//        afterOp = debugPointList.get(1).getAfterValue();
//        Assert.assertEquals(beforeOp.get("arr[1]"), null);
//        Assert.assertEquals(afterOp.get("arr[1]"), "1.0");
//
//        beforeOp = debugPointList.get(2).getBeforeValue();
//        afterOp = debugPointList.get(2).getAfterValue();
//        funcArgMap = debugPointList.get(2).getFunctionArgCallArgMap();
//        Assert.assertEquals(beforeOp.keySet().size(), 0);
//        Assert.assertEquals(funcArgMap.get("arr"), "y");
//
//        beforeOp = debugPointList.get(3).getBeforeValue();
//        afterOp = debugPointList.get(3).getAfterValue();
//        Assert.assertEquals(beforeOp.get("y[1]"), "1.0");
//        Assert.assertEquals(afterOp.get("y[1]"), "3.0");
    }

    @Test
    public void whileThroughIfTest() throws Exception {
        String str = WhileThroughIfTestCases.ruleEngineUnit.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class),
                WhileThroughIfTestCases.firstCommandId, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertTrue((Double) result.get("7c2cbc89-f03a-4f90-b378-1e5e662752bf") == 20d);
        assertForCheckpoint(processor, result);

//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        Assert.assertEquals(debugPointList.size(), 21);
//        Map<String, String> beforeOp, afterOp;
//        beforeOp = debugPointList.get(0).getBeforeValue();
//        afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertEquals(beforeOp.get("x"), null);
//        Assert.assertEquals(afterOp.get("x"), "1.0");
//
//        for(int i=1; i<10;i++) {
//            //check for condition
//            beforeOp = debugPointList.get(2*i -1).getBeforeValue();
//            Boolean condition = debugPointList.get(2*i -1).getConditionVal();
//            Assert.assertEquals(beforeOp.get("x"), i + ".0");
//            Assert.assertEquals(condition, true);
//
//            //check for op
//            beforeOp = debugPointList.get(2*i).getBeforeValue();
//            afterOp = debugPointList.get(2*i).getAfterValue();
//            Assert.assertEquals(beforeOp.get("x"), i + ".0");
//            Assert.assertEquals(afterOp.get("x"), (i+1) + ".0");
//        }
//
//        beforeOp = debugPointList.get(10*2).getBeforeValue();
//        afterOp = debugPointList.get(10*2).getAfterValue();
//        Boolean condition = debugPointList.get(10 * 2 -1).getConditionVal();
//        Assert.assertEquals(condition, false);
//        Assert.assertEquals(beforeOp.get("x"), "10.0");
//        Assert.assertEquals(afterOp.get("x"), "20.0");

    }

    @Test
    public void whileTest() throws Exception {
        String str = WhileTestCases.ruleEngineUnit.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class),
                WhileThroughIfTestCases.firstCommandId, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Assert.assertTrue((Double) result.get("7c2cbc89-f03a-4f90-b378-1e5e662752bf") == 20d);
        assertForCheckpoint(processor, result);
        //var x:integer;{x}={1};while({x}<{10}){{x}={{x}+{1}}};{x}={{x}+{10}};
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        Assert.assertEquals(debugPointList.size(), 21);
//        Map<String, String> beforeOp, afterOp;
//        beforeOp = debugPointList.get(0).getBeforeValue();
//        afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertEquals(beforeOp.get("x"), null);
//        Assert.assertEquals(afterOp.get("x"), "1.0");
//
//        for(int i=1; i<10;i++) {
//            //check for condition
//            beforeOp = debugPointList.get(2*i -1).getBeforeValue();
//            Boolean condition = debugPointList.get(2*i -1).getConditionVal();
//            Assert.assertEquals(beforeOp.get("x"), i + ".0");
//            Assert.assertEquals(condition, true);
//
//            //check for op
//            beforeOp = debugPointList.get(2*i).getBeforeValue();
//            afterOp = debugPointList.get(2*i).getAfterValue();
//            Assert.assertEquals(beforeOp.get("x"), i + ".0");
//            Assert.assertEquals(afterOp.get("x"), (i+1) + ".0");
//        }
//
//        beforeOp = debugPointList.get(10*2).getBeforeValue();
//        afterOp = debugPointList.get(10*2).getAfterValue();
//        Boolean condition = debugPointList.get(10 * 2 -1).getConditionVal();
//        Assert.assertEquals(condition, false);
//        Assert.assertEquals(beforeOp.get("x"), "10.0");
//        Assert.assertEquals(afterOp.get("x"), "20.0");
    }


    @Test
    public void variablesSetByOtherThreadTest() throws Exception {
        String str = ArrayTestCases.ruleEngineIfSomeVariablesAreSetFromOtherThread.replaceAll("\\n", "");
        Processor processor = new Processor(objectMapper.readValue(str, RuleEngineInput.class), ArrayTestCases.firstCommand, null);
        processor.setToBeDebugged(true, null, new HashSet<>());
        Map<String, Object> result = processor.process();
        Map<String, Map<String, Object>> arrayMap = (Map) result.get(Constants.arrayIndex);
        Assert.assertTrue((Double) ((Map) ((Map) ((Map) result.get(Constants.arrayIndex)).get("arr1"))).get("10_10") == 20d);
        assertForCheckpoint(processor, result);
        //from other tread: x=10; arr1[10][10] = 10;
        //curr thread: arr1[x][10]=arr1[x][10]+10;
//        List<UserReadableDebugPoint> debugPointList = processor.getDebugPoints();
//        Assert.assertEquals(debugPointList.size(), 1);
//        Map<String, String> beforeOp, afterOp;
//        beforeOp = debugPointList.get(0).getBeforeValue();
//        afterOp = debugPointList.get(0).getAfterValue();
//        Assert.assertEquals(beforeOp.get("x"), "10");
//        Assert.assertEquals(beforeOp.get("arr1[10_10]"), "10");
//        Assert.assertEquals(afterOp.get("arr1[10_10]"), "20.0");

    }
}

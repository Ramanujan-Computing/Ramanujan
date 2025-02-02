package in.ramanujan.rule.engine.test;

import in.ramanujan.enums.ConditionType;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.manager.ConditionManager;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.ConditionRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.ConstantRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4ClassRunner.class)
public class ConditionTester {
    private Map<String, RuleEngineInputUnit> ruleEngineMap;
    private ConditionRE condition;
    private CommandRE com1, com2;

    @Before
    public void init() {
        ruleEngineMap = new HashMap<>();
        condition = new ConditionRE();
        condition.setId("condition");
        condition.setConditionType(ConditionType.lessThan.getConditionTypeString());

        ConstantRE constant1 = new ConstantRE();
        constant1.setId("constant1");
        constant1.setDataType("integer");
        constant1.setValue(10);

        ConstantRE constant2 = new ConstantRE();
        constant2.setId("constant2");
        constant2.setDataType("integer");
        constant2.setValue(5);

        com1 = new CommandRE();
        com1.setId("com1");
        com1.setConstantRE(constant1);

        com2 = new CommandRE();
        com2.setId("com2");
        com2.setConstantRE(constant2);

        condition.setComparisionCommand1(com1);
        condition.setComparisionCommand2(com2);

        ruleEngineMap.put(com1.getId(), com1);
        ruleEngineMap.put(com2.getId(), com2);
        ruleEngineMap.put(constant1.getId(), constant1);
        ruleEngineMap.put(constant2.getId(), constant2);
    }

    @Test
    public void testLessThan() {
        Assert.assertEquals(false, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com2);
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testLessThanEqualTo() {
        condition.setConditionType("<=");
        Assert.assertEquals(false, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com2);
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com1);
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testGreatThan() {
        condition.setConditionType(">");
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com2);
        Assert.assertEquals(false, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testGreatThanEqualTo() {
        condition.setConditionType(">=");
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com2);
        Assert.assertEquals(false, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com1);
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testEqualTo() {
        condition.setConditionType("==");
        Assert.assertEquals(false, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com1);
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testNotEqualTo() {
        condition.setConditionType("!=");
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
        condition.setComparisionCommand2(com1);
        condition.setComparisionCommand1(com1);
        Assert.assertEquals(false, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testNot() {
        CommandRE command = new CommandRE();
        command.setId("com");
        command.setConditionRE(condition);
        ruleEngineMap.put(condition.getId(), condition);
        ruleEngineMap.put(command.getId(), command);
        ConditionRE condition1 = new ConditionRE();
        condition1.setConditionType("not");
        condition1.setComparisionCommand1(command);
        condition1.setComparisionCommand2(command);
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition1, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testAnd() {
        CommandRE command = new CommandRE();
        command.setId("com");
        condition.setConditionType("<");
        condition.setComparisionCommand1(com2);
        condition.setComparisionCommand2(com1);
        command.setConditionRE(condition);
        ruleEngineMap.put(condition.getId(), condition);
        ruleEngineMap.put(command.getId(), command);
        Condition condition1 = new Condition();
        condition1.setConditionType(ConditionType.and.getConditionTypeString());
        condition1.setComparisionCommand1(command.getId());
        condition1.setComparisionCommand2(command.getId());
        Assert.assertEquals(true, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testOr() {
        CommandRE command = new CommandRE();
        command.setId("com");
        command.setConditionRE(condition);
        ruleEngineMap.put(condition.getId(), condition);
        ruleEngineMap.put(command.getId(), command);
        Condition condition1 = new Condition();
        condition1.setConditionType(ConditionType.or.getConditionTypeString());
        condition1.setComparisionCommand1(command.getId());
        condition1.setComparisionCommand2(command.getId());
        Assert.assertEquals(false, ConditionManager.process(ruleEngineMap, condition, "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }


}

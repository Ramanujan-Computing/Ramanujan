package in.ramanujan.rule.engine.test;

import in.ramanujan.enums.OperatorType;
import in.ramanujan.pojo.ContextStack;
import in.ramanujan.pojo.checkpoint.Checkpoint;
import in.ramanujan.rule.engine.debugger.NoDebugPoint;
import in.ramanujan.rule.engine.manager.OperationManager;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.CommandRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.ConstantRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.OperationRE;
import in.ramanujan.rule.engine.pojo.ruleEngineInputUnits.RuleEngineInputUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4ClassRunner.class)
public class OperatorTester {

    private Map<String, RuleEngineInputUnit> ruleEngineMap;
    private OperationRE operation;
    private CommandRE com1, com2;

    @Before
    public void init() {
        ruleEngineMap = new HashMap<>();

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


        operation = new OperationRE();
        operation.setId("op");
        operation.setOperatorType(OperatorType.MINUS.getOperatorCode());
        operation.setOperand1(com1);
        operation.setOperand2(com2);


        ruleEngineMap.put(com1.getId(), com1);
        ruleEngineMap.put(com2.getId(), com2);
        ruleEngineMap.put(constant1.getId(), constant1);
        ruleEngineMap.put(constant2.getId(), constant2);
    }

    @Test
    public void testMinusOperation() {
        Assert.assertEquals(5d, OperationManager.process(ruleEngineMap, operation,
                "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testAddOperation() {
        operation.setOperatorType(OperatorType.ADD.getOperatorCode());
        Assert.assertEquals(15d, OperationManager.process(ruleEngineMap, operation,
                "process",
                new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testMultiplyOperation() {
        operation.setOperatorType(OperatorType.MULTIPLY.getOperatorCode());
        Assert.assertEquals(50d, OperationManager.process(ruleEngineMap, operation,
                "process", new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testDivideOperation() {
        operation.setOperatorType(OperatorType.DIVIDE.getOperatorCode());
        Assert.assertEquals(2d, OperationManager.process(ruleEngineMap, operation,
                "process", new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testPowerOperation() {
        operation.setOperatorType(OperatorType.POWER.getOperatorCode());
        Assert.assertEquals(100000d, OperationManager.process(ruleEngineMap, operation,
                "process", new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testLogOperation() {
        operation.setOperatorType(OperatorType.LOG.getOperatorCode());
        ((ConstantRE)ruleEngineMap.get("constant1")).setValue(100);
        ((ConstantRE)ruleEngineMap.get("constant2")).setValue(10);
        Assert.assertEquals(2d, OperationManager.process(ruleEngineMap, operation,
                "process", new ContextStack(), NoDebugPoint.INSTANCE));
    }

    @Test
    public void testSineOperation() {
        operation.setOperatorType(OperatorType.SINE.getOperatorCode());
        ((ConstantRE)ruleEngineMap.get("constant1")).setValue(0);
        ((ConstantRE)ruleEngineMap.get("constant2")).setValue(3.14/2);
        Assert.assertTrue((1d - OperationManager.process(ruleEngineMap, operation,
                "process", new ContextStack(), NoDebugPoint.INSTANCE).get()) < 0.05);
    }

    @Test
    public void testCosineOperation() {
        operation.setOperatorType(OperatorType.COSINE.getOperatorCode());
        ((ConstantRE)ruleEngineMap.get("constant1")).setValue(0);
        ((ConstantRE)ruleEngineMap.get("constant2")).setValue(0);
        Assert.assertTrue((1d - (Double)OperationManager.process(ruleEngineMap, operation,
                "process", new ContextStack(), NoDebugPoint.INSTANCE).get()) < 0.05);
    }
}

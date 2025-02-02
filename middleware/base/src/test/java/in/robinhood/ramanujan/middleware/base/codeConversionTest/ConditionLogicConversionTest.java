package in.robinhood.ramanujan.middleware.base.codeConversionTest;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.ConditionLogicConverter;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ConditionLogicConversionTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CodeConverter codeConverter;

    @Spy
    ConditionLogicConverter conditionLogicConverter;

    @Before
    public void init() {
        Mockito.doReturn(new StringUtils()).when(conditionLogicConverter).getStringUtils();
    }

    @Test
    public void testAndSeperatedCondition() throws CompilationException {
        String code = "{x==1}&&{x==2}";
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class), Mockito.anyList(),
                Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        Condition condition = null;
        try {
            condition = (Condition) conditionLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue("&&".equalsIgnoreCase(condition.getConditionType()));
        Assert.assertTrue("x==1".equalsIgnoreCase(condition.getComparisionCommand1()));
        Assert.assertTrue("x==2".equalsIgnoreCase(condition.getComparisionCommand2()));
    }

    @Test
    public void testOrSeperatedCondition()  throws CompilationException {
        String code = "{x==1}||{x==2}";
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class),
                Mockito.anyList(), Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        Condition condition = null;
        try {
            condition = (Condition) conditionLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(),null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue("||".equalsIgnoreCase(condition.getConditionType()));
        Assert.assertTrue("x==1".equalsIgnoreCase(condition.getComparisionCommand1()));
        Assert.assertTrue("x==2".equalsIgnoreCase(condition.getComparisionCommand2()));
    }

    @Test
    public void testSimpleCondition()  throws CompilationException {
        String code = "{x}=={1}";
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class),
                Mockito.anyList(), Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        Condition condition = null;
        try {
            condition = (Condition) conditionLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(),null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue("==".equalsIgnoreCase(condition.getConditionType()));
        Assert.assertTrue("x".equalsIgnoreCase(condition.getComparisionCommand1()));
        Assert.assertTrue("1".equalsIgnoreCase(condition.getComparisionCommand2()));
    }

    @Test
    public void testNestedCondition()  throws CompilationException {
        String code = "{{x}=={1}}&&{{x}=={2}}";
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class),
                Mockito.anyList(),Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        Condition condition = null;
        try {
            condition = (Condition) conditionLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(),null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue("&&".equalsIgnoreCase(condition.getConditionType()));
        Assert.assertTrue("{x}=={1}".equalsIgnoreCase(condition.getComparisionCommand1()));
        Assert.assertTrue("{x}=={2}".equalsIgnoreCase(condition.getComparisionCommand2()));
    }
}

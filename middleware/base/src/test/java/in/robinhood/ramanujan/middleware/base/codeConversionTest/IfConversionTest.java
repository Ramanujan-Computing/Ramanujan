package in.robinhood.ramanujan.middleware.base.codeConversionTest;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.ConditionLogicConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.IfLogicConverter;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.If;
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
public class IfConversionTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Spy
    IfLogicConverter ifLogicConverter;

    @Mock
    CodeConverter codeConverter;

    @Mock
    ConditionLogicConverter conditionLogicConverter;

    @Mock
    StringUtils stringUtils;

    @Before
    public void init() throws CompilationException {
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            if("".equalsIgnoreCase(invocationArgumentCode)){
                return new ArrayList();
            }
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class), Mockito.anyList(),
                Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        try {
            Mockito.doAnswer(invocation -> {
                String conditionCode = (String) invocation.getArguments()[0];
                Condition condition = new Condition();
                condition.setId(conditionCode);
                return condition;
            }).when(conditionLogicConverter).convertCode(Mockito.anyString(), Mockito.any(RuleEngineInput.class),
                    Mockito.any(CodeConverter.class), Mockito.anyList(), Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Mockito.doReturn(conditionLogicConverter).when(ifLogicConverter).getConditionLogicConverter();

        Mockito.doReturn(new StringUtils()).when(ifLogicConverter).getStringUtils();
    }

    @Test
    public void testIfCommandWithNoElse() {
        String code = "if(someCondition){ifCommand}";
        If ifBlock = null;
        try {
            ifBlock = (If) ifLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(),
                   new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }
        Assert.assertTrue("someCondition".equalsIgnoreCase(ifBlock.getConditionId()));
        Assert.assertTrue("ifCommand".equalsIgnoreCase(ifBlock.getIfCommand()));
        Assert.assertTrue(ifBlock.getElseCommandId() == null);
    }

    @Test
    public void testIfCommandWithElse() {
        String code = "if(someCondition){ifCommand}else{elseCommand}";
        If ifBlock = null;
        try {
            ifBlock = (If) ifLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(),null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }
        Assert.assertTrue("someCondition".equalsIgnoreCase(ifBlock.getConditionId()));
        Assert.assertTrue("ifCommand".equalsIgnoreCase(ifBlock.getIfCommand()));
        Assert.assertTrue("elseCommand".equalsIgnoreCase(ifBlock.getElseCommandId()));
    }

}

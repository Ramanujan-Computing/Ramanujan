package in.ramanujan.middleware.base.codeConversionTest;

import in.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.ConditionLogicConverter;
import in.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.WhileLogicConverter;
import in.ramanujan.middleware.base.exception.CompilationException;
import in.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.middleware.base.utils.StringUtils;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.While;
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
public class WhileConversionTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Spy
    WhileLogicConverter whileLogicConverter;

    @Mock
    CodeConverter codeConverter;

    @Mock
    ConditionLogicConverter conditionLogicConverter;

    Command command;


    @Before
    public void init() throws CompilationException {
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            if("".equalsIgnoreCase(invocationArgumentCode)){
                return new ArrayList();
            }
            command = new Command();
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
        } catch (CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Mockito.doReturn(conditionLogicConverter).when(whileLogicConverter).getConditionLogicConverter();

        Mockito.doReturn(new StringUtils()).when(whileLogicConverter).getStringUtils();
    }

    @Test
    public void testWhile() {
        String code = "while(condition){x=1}";


        While output = null;
        try {
            output = (While) whileLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(), null, null);
        } catch (CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue("condition".equalsIgnoreCase(output.getConditionId()));
        Assert.assertTrue("x=1".equalsIgnoreCase(output.getWhileCommandId()));
        Assert.assertTrue(command.getNextId() == null);

    }
}

package in.robinhood.ramanujan.middleware.base.codeConversionTest;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.OperationLogicConverter;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Operation;
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
public class OperationLogicConversionTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CodeConverter codeConverter;

    @Spy
    OperationLogicConverter operationLogicConverter;

    @Before
    public void init() {
        Mockito.doReturn(new StringUtils()).when(operationLogicConverter).getStringUtils();
    }

    @Test
    public void testNestedOperation() throws CompilationException {
        String code = "(x+11)-(x-2)";
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class), Mockito.anyList(),
                Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        RuleEngineInput ruleEngineInput = new RuleEngineInput();
        Operation operation = null;
        try {
            operation = (Operation) operationLogicConverter.convertCode(code, ruleEngineInput, codeConverter, new ArrayList<>(), new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue("-".equalsIgnoreCase(operation.getOperatorType()));
        Assert.assertTrue("{x}+{11}".equalsIgnoreCase(operation.getOperand1()));
        Assert.assertTrue("{x}-{2}".equalsIgnoreCase(operation.getOperand2()));
    }


    @Test
    public void testSimpleOperation() throws  CompilationException {
        String code = "{x}+{1}";
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class), Mockito.anyList(),
                Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        Operation operation = null;
        try {
            operation = (Operation) operationLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue("+".equalsIgnoreCase(operation.getOperatorType()));
        Assert.assertTrue("x".equalsIgnoreCase(operation.getOperand1()));
        Assert.assertTrue("1".equalsIgnoreCase(operation.getOperand2()));
    }

    @Test
    public void testSimpleEqualOperation() throws  CompilationException {
        String code = "x=x+1";
        Mockito.doAnswer(invocation -> {
            String invocationArgumentCode = (String) invocation.getArguments()[0];
            Command command = new Command();
            command.setId(invocationArgumentCode);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.any(String.class), Mockito.any(RuleEngineInput.class), Mockito.anyList(),
                Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        Operation operation = null;
        try {
            operation = (Operation) operationLogicConverter.convertCode(code, new RuleEngineInput(), codeConverter, new ArrayList<>(), new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }


        Assert.assertTrue("=".equalsIgnoreCase(operation.getOperatorType()));
        Assert.assertTrue("x".equalsIgnoreCase(operation.getOperand1()));
        System.out.println(operation.getOperand2());
        Assert.assertTrue("x+1".equalsIgnoreCase(operation.getOperand2()));
    }

}

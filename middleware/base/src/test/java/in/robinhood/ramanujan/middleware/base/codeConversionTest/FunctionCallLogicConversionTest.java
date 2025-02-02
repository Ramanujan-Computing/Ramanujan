package in.robinhood.ramanujan.middleware.base.codeConversionTest;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverterLogicFactory;
import in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.FunctionLogicConverter;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.robinhood.ramanujan.middleware.base.spring.SpringConfig;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.middleware.base.utils.TranslateUtil;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.FunctionCall;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class FunctionCallLogicConversionTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private StringUtils stringUtils;

    private FunctionLogicConverter functionLogicConverter;

    private ApplicationContext applicationContext;
    private TranslateUtil translateUtil;
    private CodeConverterLogicFactory codeConverterLogicFactory;

    @Before
    public void init() {
        applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        translateUtil = applicationContext.getBean(TranslateUtil.class);
        codeConverterLogicFactory = applicationContext.getBean(CodeConverterLogicFactory.class);
        stringUtils = applicationContext.getBean(StringUtils.class);
        functionLogicConverter = applicationContext.getBean(FunctionLogicConverter.class);
    }


    @Test
    public void testLogic() {
//        FunctionLogicConverter functionLogicConverter = new FunctionLogicConverter();
        Map<String, Variable> variableMap = new HashMap<>();
        Variable var1 = new Variable();
        var1.setId("var1");
        Variable var2 = new Variable();
        var2.setId("var2");

        variableMap.put("var1", var1);
        variableMap.put("var2", var2);

        Map<String, Array> arrayMap = new HashMap<>();

        FunctionCall functionCall = null;
        try {
            CodeConverter codeConverter = new CodeConverter(codeConverterLogicFactory, stringUtils);
            codeConverter.setVariableMap(variableMap);
            functionCall = (FunctionCall) functionLogicConverter.convertCode("exec funcCall(var1,var2)",
                    new RuleEngineInput(), codeConverter, new ArrayList<String>() {{add("");}}, new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Assert.assertTrue(functionCall.getId().equalsIgnoreCase("funcCall"));
        Assert.assertTrue(functionCall.getArguments().size() == 2);
        for(String argument : functionCall.getArguments()) {
            Assert.assertTrue(argument.equalsIgnoreCase(variableMap.get(argument).getId()));
        }
    }
}

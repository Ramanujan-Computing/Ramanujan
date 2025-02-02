package in.robinhood.ramanujan.middleware.base.codeConversionTest;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverterLogicFactory;
import in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl.VariableInitLogicConverter;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.robinhood.ramanujan.middleware.base.spring.SpringConfig;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.middleware.base.utils.TranslateUtil;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
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

@RunWith(MockitoJUnitRunner.class)
public class VariableInitConversionTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private ApplicationContext applicationContext;
    private TranslateUtil translateUtil;
    private CodeConverterLogicFactory codeConverterLogicFactory;
    private StringUtils stringUtils;

    @Before
    public void init() {
        applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        translateUtil = applicationContext.getBean(TranslateUtil.class);
        codeConverterLogicFactory = applicationContext.getBean(CodeConverterLogicFactory.class);
        stringUtils = applicationContext.getBean(StringUtils.class);
    }

    @Test
    public void testOneVariableInit() {
        CodeConverter codeConverter = new CodeConverter(codeConverterLogicFactory, stringUtils);
        String code = "var initVar:integer";
        VariableInitLogicConverter variableInitLogicConverter = new VariableInitLogicConverter();
        RuleEngineInput ruleEngineInput = new RuleEngineInput();
        try {
            variableInitLogicConverter.convertCode(code, ruleEngineInput, codeConverter, new ArrayList<>(), new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Variable variable = codeConverter.getVariableMap().get("initVar");
        Assert.assertTrue("integer".equalsIgnoreCase(variable.getDataType()));
        Assert.assertTrue("initVar".equalsIgnoreCase(variable.getName()));
    }

    @Test
    public void testMoreThanOneVariableInit() {
        CodeConverter codeConverter = new CodeConverter(codeConverterLogicFactory, stringUtils);
        String code = "var initVar1,initVar2:integer";
        VariableInitLogicConverter variableInitLogicConverter = new VariableInitLogicConverter();
        RuleEngineInput ruleEngineInput = new RuleEngineInput();
        try {
            variableInitLogicConverter.convertCode(code, ruleEngineInput, codeConverter, new ArrayList<>(), new NoConcatImpl(), null, null);
        } catch (in.robinhood.ramanujan.middleware.base.exception.CompilationException compilationException) {
            compilationException.printStackTrace();
        }

        Variable variable = codeConverter.getVariableMap().get("initVar1");
        Assert.assertTrue("integer".equalsIgnoreCase(variable.getDataType()));
        Assert.assertTrue("initVar1".equalsIgnoreCase(variable.getName()));

        variable = codeConverter.getVariableMap().get("initVar2");
        Assert.assertTrue("integer".equalsIgnoreCase(variable.getDataType()));
        Assert.assertTrue("initVar2".equalsIgnoreCase(variable.getName()));
    }
}

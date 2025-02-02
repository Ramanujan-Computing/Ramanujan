package in.ramanujan.middleware.base.codeConversionTest;

import in.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.ramanujan.middleware.base.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.middleware.base.spring.SpringConfig;
import in.ramanujan.middleware.base.utils.StringUtils;
import in.ramanujan.middleware.base.utils.TranslateUtil;
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

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CodeConversionTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private ApplicationContext applicationContext;
    private TranslateUtil translateUtil;
    private CodeConverterLogicFactory codeConverterLogicFactory;
    private StringUtils stringUtils;

    private CodeConverter codeConverter;

    @Before
    public void init() {
        applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        translateUtil = applicationContext.getBean(TranslateUtil.class);
        codeConverterLogicFactory = applicationContext.getBean(CodeConverterLogicFactory.class);
        codeConverterLogicFactory = applicationContext.getBean(CodeConverterLogicFactory.class);
        stringUtils = applicationContext.getBean(StringUtils.class);
        codeConverter = new CodeConverter(codeConverterLogicFactory, stringUtils);
    }

    @Test
    public void testGetCodeChunks() {
        String code = "var x:integer;x=10;if({x}<{10}){{{x}={11}}};var y:integer;";
        List<String> codeChunks = codeConverter.getCodeChunks(code);

        Assert.assertTrue("var x:integer".equalsIgnoreCase(codeChunks.get(0)));
        Assert.assertTrue("x=10".equalsIgnoreCase(codeChunks.get(1)));
        Assert.assertTrue("if({x}<{10}){{{x}={11}}}".equalsIgnoreCase(codeChunks.get(2)));
        Assert.assertTrue("var y:integer".equalsIgnoreCase(codeChunks.get(3)));
    }

    @Test
    public void testGetCodeChunksIfSingleVariableInCode() {
        String code = "sampleVariable";
        List<String> codeChunks = codeConverter.getCodeChunks(code);

        Assert.assertTrue("sampleVariable".equalsIgnoreCase(codeChunks.get(0)));
    }

    @Test
    public void testGetChunkType() {
        String code = "    var x:integer";
        String chunk = codeConverter.getTypeOfChunk(code);
        Assert.assertTrue("var".equalsIgnoreCase(chunk));
    }

}

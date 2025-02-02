package in.robinhood.ramanujan.middleware.base;

import in.robinhood.ramanujan.middleware.base.pojo.IndexWrapper;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.CodeContainer;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.SimpleCodeCommand;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;

@RunWith(MockitoJUnitRunner.class)
public class StringUtilTest {
    private StringUtils stringUtils;

    @Before
    public void test() {
        stringUtils = new StringUtils();
    }

    @Test
    public void testParseForDefCodeContainer() throws Exception {
        String codeCommand = "def";
        //test1
        String code = "def func(a, b,c,    d){code}";
        CodeContainer assertion = new CodeContainer();
        assertion.setCode("code");
        assertion.setCodeCommand("def");
        assertion.setPlaceHolder("func");
        assertion.setArguments(new ArrayList<String>() {{
            add("a");
            add("b");
            add("c");
            add("d");
        }});

        CodeContainer codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test2
        code = "def func    (a,  b,c,d){code}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test3
        code = "def func    (a,  b,c,d)  {code}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test4
        code = "def func    (a,  b,c,d )  {code}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));
    }

    @Test
    public void testParseForDefNestedCodeContainer() throws Exception {
        String codeCommand = "def";
        //test1
        String internalCode = "if({x}<{12}){{{x}={20};}else{{x}={30};}}if({y}<{10}){{{y}={40};}else{{y}={50};}}";
        String code = "def func(a, b,c,    d){"+internalCode+"}";
        CodeContainer assertion = new CodeContainer();
        assertion.setCode(internalCode);
        assertion.setCodeCommand("def");
        assertion.setPlaceHolder("func");
        assertion.setArguments(new ArrayList<String>() {{
            add("a");
            add("b");
            add("c");
            add("d");
        }});

        CodeContainer codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test2
        code = "def func    (a,  b,c,d){"+internalCode+"}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test3
        code = "def func    (a,  b,c,d)  {"+internalCode+"}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test4
        code = "def func    (a,  b,c,d )  {"+internalCode+"}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));
    }

    @Test
    public void testParseForIfCodeContainer() throws Exception {
        String codeCommand = "if";
        //test1
        String code = "if(a){code}";
        CodeContainer assertion = new CodeContainer();
        assertion.setCode("code");
        assertion.setCodeCommand("if");
        assertion.setPlaceHolder(null);
        assertion.setArguments(new ArrayList<String>() {{
            add("a");
        }});

        CodeContainer codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test2
        code = "if    (a){code}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test3
        code = "if    ( a)  {code}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test4
        code = "if    (a)  {code}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test5
        code = "if    (a)  {}";
        assertion.setCode("");
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));
    }

    @Test
    public void testParseForIfNestedCodeContainer() throws Exception {
        String codeCommand = "if";
        String internalCode = "if({x}<{12}){{{x}={20};}else{{x}={30};}}if({y}<{10}){{{y}={40};}else{{y}={50};}}";
        //test1
        String code = "if(a){"+internalCode+"}";
        CodeContainer assertion = new CodeContainer();
        assertion.setCode(internalCode);
        assertion.setCodeCommand("if");
        assertion.setPlaceHolder(null);
        assertion.setArguments(new ArrayList<String>() {{
            add("a");
        }});

        CodeContainer codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test2
        code = "if    (a){"+internalCode+"}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test3
        code = "if    ( a)  {"+internalCode+"}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));

        //test4
        code = "if    (a)  {"+internalCode+"}";
        codeContainer = stringUtils.parseForCodeContainer(codeCommand, code,  new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(codeContainer));
    }

    @Test
    public void testParseForSimpleCodeCommand() {
        //Test1
        String code = "exec func(a,b)";
        String codeCommand = "exec";
        SimpleCodeCommand assertion = new SimpleCodeCommand();
        assertion.setCodeCommand(codeCommand);
        assertion.setPlaceHolder("func");
        assertion.setArguments(new ArrayList<String>() {{
            add("a");
            add("b");
        }});

        SimpleCodeCommand simpleCodeCommand = stringUtils.parseForSimpleCodeCommand(codeCommand, code, new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(simpleCodeCommand));

        code = "exec     func(a,b)";
        simpleCodeCommand = stringUtils.parseForSimpleCodeCommand(codeCommand, code, new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(simpleCodeCommand));

        code = "exec     func (a,b)";
        simpleCodeCommand = stringUtils.parseForSimpleCodeCommand(codeCommand, code, new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(simpleCodeCommand));

        code = "exec func( a,b)";
        simpleCodeCommand = stringUtils.parseForSimpleCodeCommand(codeCommand, code, new IndexWrapper(0));
        Assert.assertTrue(assertion.isEqual(simpleCodeCommand));
    }


}

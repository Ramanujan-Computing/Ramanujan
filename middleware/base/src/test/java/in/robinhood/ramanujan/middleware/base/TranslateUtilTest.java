package in.robinhood.ramanujan.middleware.base;


import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.dagChecker.CodeSnippetDagChecker;
import in.robinhood.ramanujan.middleware.base.dagChecker.DagElementDagChecker;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.utils.StringUtils;
import in.robinhood.ramanujan.middleware.base.utils.TranslateUtil;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
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
import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class TranslateUtilTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Spy
    private TranslateUtil translateUtil;

    @Mock
    private CodeConverter codeConverter;

    @Before
    public void init() {
        Mockito.doReturn(new StringUtils()).when(translateUtil).getStringUtils();
    }

    @Test
    public void twoThreadExecThread() {
        String code = "var x:integer;\n" +
                "x=10;\n" +
                "var y:integer;\n" +
                "y=20;\n" +
                "var z:integer;\n" +
                "threadStart(t1) {\n" +
                "\tif(x<12) {\n" +
                "\t\tx = 20;\n" +
                "\t} else {\n" +
                "\t\tx = 30;\n" +
                "\t}\n" +
//                "\tthreadComplete(t1)\n" +
                "}\n" +
                "\n" +
                "threadStart(t2) {\n" +
                "\tif(y>10) {\n" +
                "\t\ty = 40;\n" +
                "\t} else {\n" +
                "\t\ty = 50;\n" +
                "\t}\n" +
//                "\tthreadComplete(t2)\n" +
                "}\n" +
                "\n" +
                "threadOnEnd(t1, t2,1) {\n" +
                "\tz = x + y\n" +
                "}\n";

        code  = code.replaceAll("\\n","").replaceAll("\\t","");
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(), new HashMap<>());
        CodeSnippetElement dagAssertion = new CodeSnippetElement();
        dagAssertion.setCode("var x:integer;x=10;var y:integer;y=20;var z:integer;");
        CodeSnippetElement parallel1 = new CodeSnippetElement();
        parallel1.setCode("if(y>10) {y = 40;} else {y = 50;}");
        CodeSnippetElement parallel2 = new CodeSnippetElement();
        parallel2.setCode("if(x<12) {x = 20;} else {x = 30;}");
        dagAssertion.getNext().add(parallel1);
        dagAssertion.getNext().add(parallel2);
        CodeSnippetElement terminal = new CodeSnippetElement();
        terminal.setCode("z = x + y");
        parallel1.getNext().add(terminal);
        parallel2.getNext().add(terminal);

        Assert.assertTrue((new CodeSnippetDagChecker()).checkDag(dagAssertion, codeSnippetElement));
    }

    @Test
    public void twoThreadExecThreadWithRandomSpace() {
        String code = "var x:integer;\n" +
                "x=10;\n" +
                "var y:integer;\n" +
                "y=20;\n" +
                "var z:integer;\n" +
                "threadStart (t1) {\n" +
                "\tif(x<12) {\n" +
                "\t\tx = 20;\n" +
                "\t} else {\n" +
                "\t\tx = 30;\n" +
                "\t}\n" +
//                "\tthreadComplete(t1)\n" +
                "}\n" +
                "\n" +
                "threadStart( t2 ) {\n" +
                "\tif(y>10) {\n" +
                "\t\ty = 40;\n" +
                "\t} else {\n" +
                "\t\ty = 50;\n" +
                "\t}\n" +
//                "\tthreadComplete(t2)\n" +
                "}\n" +
                "\n" +
                "threadOnEnd (t1, t2, 1) {\n" +
                "\tz = x + y\n" +
                "}\n";

        code  = code.replaceAll("\\n","").replaceAll("\\t","");
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(), new HashMap<>());
        CodeSnippetElement dagAssertion = new CodeSnippetElement();
        dagAssertion.setCode("var x:integer;x=10;var y:integer;y=20;var z:integer;");
        CodeSnippetElement parallel1 = new CodeSnippetElement();
        parallel1.setCode("if(y>10) {y = 40;} else {y = 50;}");
        CodeSnippetElement parallel2 = new CodeSnippetElement();
        parallel2.setCode("if(x<12) {x = 20;} else {x = 30;}");
        dagAssertion.getNext().add(parallel1);
        dagAssertion.getNext().add(parallel2);
        CodeSnippetElement terminal = new CodeSnippetElement();
        terminal.setCode("z = x + y");
        parallel1.getNext().add(terminal);
        parallel2.getNext().add(terminal);

        Assert.assertTrue((new CodeSnippetDagChecker()).checkDag(dagAssertion, codeSnippetElement));
    }

    @Test
    public void threeThreadExecTest() {
        String code = "var x:integer;\n" +
                "var y:integer;\n" +
                "var z:integer;\n" +
                "\n" +
                "x = 10;\n" +
                "y = 20;\n" +
                "\n" +
                "threadStart(t1) {\n" +
                "\tif(x<10) {\n" +
                "\t\tx = 20;\n" +
                "\t} else {\n" +
                "\t\tx = 5;\n" +
                "\t}\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "\n" +
                "threadStart(t2) {\n" +
                "\tif(y<10) {\n" +
                "\t\ty = 20;\n" +
                "\t} else {\n" +
                "\t\ty = 5;\n" +
                "\t}\n" +
//                "\tthreadComplete(t2);\n" +
                "}\n" +
                "\n" +
                "threadStart(t3) {\n" +
                "\tthreadOnEnd(t1,1) {\n" +
                "\t\tx = x*2;\n" +
//                "\t\tthreadComplete(t3);\n" +
                "\t}\n" +
                "}\n" +
                "\n" +
                "threadOnEnd(t1,t2,t3,1) {\n" +
                "\tz = x+y;\n" +
                "}\n";

        code  = code.replaceAll("\\n","").replaceAll("\\t","");
        Map<String, CodeSnippetElement> map = new HashMap<>();
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, map, new HashMap<>(),  new HashMap<>());


        CodeSnippetElement dagAssertion = new CodeSnippetElement();
        dagAssertion.setCode(("var x:integer;\n" +
                "var y:integer;\n" +
                "var z:integer;\n" +
                "\n" +
                "x = 10;\n" +
                "y = 20;\n").replaceAll("\n","").replaceAll("\\t",""));

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode(("if(x<10) {\n" +
                "\t\tx = 20;\n" +
                "\t} else {\n" +
                "\t\tx = 5;\n" +
                "}\n" +
                "\n").replaceAll("\n","").replaceAll("\\t",""));

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode(("if(y<10) {\n" +
                "\t\ty = 20;\n" +
                "\t} else {\n" +
                "\t\ty = 5;\n" +
                "}\n").replaceAll("\n","").replaceAll("\\t",""));

        CodeSnippetElement t3 = new CodeSnippetElement();
        t3.setCode("");

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("x = x*2;");

        CodeSnippetElement n3 = new CodeSnippetElement();
        n3.setCode("z = x+y;");

        dagAssertion.getNext().add(t1);
        dagAssertion.getNext().add(t2);
        dagAssertion.getNext().add(t3);

        t1.getNext().add(n2);
        t1.getNext().add(n3);

        t2.getNext().add(n3);
        t3.getNext().add(n3);
        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(dagAssertion, codeSnippetElement));
        Assert.assertNotNull(codeSnippetElement);
    }

    @Test
    public void threeThreadExecWithRandomSpaceTest() {
        String code = "var x:integer;\n" +
                "var y:integer;\n" +
                "var z:integer;\n" +
                "\n" +
                "x = 10;\n" +
                "y = 20;\n" +
                "\n" +
                "threadStart ( t1 ) {\n" +
                "\tif(x<10) {\n" +
                "\t\tx = 20;\n" +
                "\t} else {\n" +
                "\t\tx = 5;\n" +
                "\t}\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "\n" +
                "threadStart(t2 ) {\n" +
                "\tif(y<10) {\n" +
                "\t\ty = 20;\n" +
                "\t} else {\n" +
                "\t\ty = 5;\n" +
                "\t}\n" +
//                "\tthreadComplete(t2);\n" +
                "}\n" +
                "\n" +
                "threadStart( t3) {\n" +
                "\tthreadOnEnd(t1,1) {\n" +
                "\t\tx = x*2;\n" +
//                "\t\tthreadComplete(t3);\n" +
                "\t}\n" +
                "}\n" +
                "\n" +
                "threadOnEnd (t1,t2,t3,1) {\n" +
                "\tz = x+y;\n" +
                "}\n";

        code  = code.replaceAll("\\n","").replaceAll("\\t","");
        Map<String, CodeSnippetElement> map = new HashMap<>();
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, map, new HashMap<>(),  new HashMap<>());


        CodeSnippetElement dagAssertion = new CodeSnippetElement();
        dagAssertion.setCode(("var x:integer;\n" +
                "var y:integer;\n" +
                "var z:integer;\n" +
                "\n" +
                "x = 10;\n" +
                "y = 20;\n").replaceAll("\n","").replaceAll("\\t",""));

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode(("if(x<10) {\n" +
                "\t\tx = 20;\n" +
                "\t} else {\n" +
                "\t\tx = 5;\n" +
                "}\n" +
                "\n").replaceAll("\n","").replaceAll("\\t",""));

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode(("if(y<10) {\n" +
                "\t\ty = 20;\n" +
                "\t} else {\n" +
                "\t\ty = 5;\n" +
                "}\n").replaceAll("\n","").replaceAll("\\t",""));

        CodeSnippetElement t3 = new CodeSnippetElement();
        t3.setCode("");

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("x = x*2;");

        CodeSnippetElement n3 = new CodeSnippetElement();
        n3.setCode("z = x+y;");

        dagAssertion.getNext().add(t1);
        dagAssertion.getNext().add(t2);
        dagAssertion.getNext().add(t3);

        t1.getNext().add(n2);
        t1.getNext().add(n3);

        t2.getNext().add(n3);
        t3.getNext().add(n3);
        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(dagAssertion, codeSnippetElement));
        Assert.assertNotNull(codeSnippetElement);
    }


    @Test
    public void parallelExecEndAndParallelExecStartTest() {
        String code = "threadStart(t1) {\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "threadStart(t2) {\n" +
//                "\tthreadComplete(t2);\n" +
                "}\n" +
                "\n" +
                "threadOnEnd(t1,t2,1) {\n" +
                "\tthreadStart(t3) {\n" +
//                "\t\tthreadComplete(t3);\n" +
                "\t}\n" +
                "\tthreadStart(t4) {\n" +
//                "\t\tthreadComplete(t4);\n" +
                "\t}\n" +
                "\tthreadOnEnd(t3,t4,1) {\n" +
                "\t\tz= x+y;\n" +
                "\t}\n" +
                "}";

        code = code.replaceAll("\\n", "").replaceAll("\\t","");
        Map<String, CodeSnippetElement> map = new HashMap<>();
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, map, new HashMap<>(), new HashMap<>() );

        CodeSnippetElement assertionDag = new CodeSnippetElement();
        assertionDag.setCode("");

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode("");

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode("");

        assertionDag.getNext().add(t1);
        assertionDag.getNext().add(t2);

        CodeSnippetElement n1 = new CodeSnippetElement();
        n1.setCode("");
        t1.getNext().add(n1);
        t2.getNext().add(n1);

        CodeSnippetElement t4 = new CodeSnippetElement();
        t4.setCode("");

        CodeSnippetElement t3 = new CodeSnippetElement();
        t3.setCode("");

        n1.getNext().add(t3);
        n1.getNext().add(t4);

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("z= x+y;");

        t3.getNext().add(n2);
        t4.getNext().add(n2);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(assertionDag, codeSnippetElement));
    }


    @Test
    public void parallelExecEndAndParallelExecStartWithRandomSpaceTest() {
        String code = "threadStart (t1) {\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "threadStart(t2) {\n" +
//                "\tthreadComplete( t2);\n" +
                "}\n" +
                "\n" +
                "threadOnEnd(t1,t2,1) {\n" +
                "\tthreadStart(t3 ) {\n" +
//                "\t\tthreadComplete(t3);\n" +
                "\t}\n" +
                "\tthreadStart ( t4 )      {\n" +
//                "\t\tthreadComplete(t4);\n" +
                "\t}\n" +
                "\tthreadOnEnd(t3, t4,1) {\n" +
                "\t\tz= x+y;\n" +
                "\t}\n" +
                "}";

        code = code.replaceAll("\\n", "").replaceAll("\\t","");
        Map<String, CodeSnippetElement> map = new HashMap<>();
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, map, new HashMap<>(), new HashMap<>() );

        CodeSnippetElement assertionDag = new CodeSnippetElement();
        assertionDag.setCode("");

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode("");

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode("");

        assertionDag.getNext().add(t1);
        assertionDag.getNext().add(t2);

        CodeSnippetElement n1 = new CodeSnippetElement();
        n1.setCode("");
        t1.getNext().add(n1);
        t2.getNext().add(n1);

        CodeSnippetElement t4 = new CodeSnippetElement();
        t4.setCode("");

        CodeSnippetElement t3 = new CodeSnippetElement();
        t3.setCode("");

        n1.getNext().add(t3);
        n1.getNext().add(t4);

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("z= x+y;");

        t3.getNext().add(n2);
        t4.getNext().add(n2);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(assertionDag, codeSnippetElement));
    }

    @Test
    public void mappingToBeResolvedTest() {
        String code = "threadOnEnd(t1,t2,1) {\n" +
                "\tthreadStart(t3) {\n" +
//                "\t\tthreadComplete(t3);\n" +
                "\t}\n" +
                "\tthreadStart(t4) {\n" +
//                "\t\tthreadComplete(t4);\n" +
                "\t}\n" +
                "\tthreadOnEnd(t3,t4,1) {\n" +
                "\t\tz = x+y;\n" +
                "\t}\n" +
                "}\n" +
                "\n" +
                "threadStart(t1) {\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "threadStart(t2) {\n" +
//                "\tthreadComplete(t2);\n" +
                "}";

        code = code.replaceAll("\\n", "").replaceAll("\\t", "");
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());

        CodeSnippetElement dagAssertion = new CodeSnippetElement();
        dagAssertion.setCode("");

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode("");

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode("");

        dagAssertion.getNext().add(t1);
        dagAssertion.getNext().add(t2);

        CodeSnippetElement n1 = new CodeSnippetElement();
        n1.setCode("");

        t1.getNext().add(n1);
        t2.getNext().add(n1);


        CodeSnippetElement t3 = new CodeSnippetElement();
        t3.setCode("");

        CodeSnippetElement t4 = new CodeSnippetElement();
        t4.setCode("");

        n1.getNext().add(t3);
        n1.getNext().add(t4);

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("z = x+y;");

        t3.getNext().add(n2);
        t4.getNext().add(n2);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(dagAssertion, codeSnippetElement));
    }

    @Test
    public void mappingToBeResolvedWithRandomTest() {
        String code = "threadOnEnd( t1,t2, 1) {\n" +
                "\tthreadStart ( t3) {\n" +
//                "\t\tthreadComplete(t3);\n" +
                "\t}\n" +
                "\tthreadStart(t4 ) {\n" +
//                "\t\tthreadComplete(t4);\n" +
                "\t}\n" +
                "\tthreadOnEnd(t3,t4,1) {\n" +
                "\t\tz = x+y;\n" +
                "\t}\n" +
                "}\n" +
                "\n" +
                "threadStart (t1 ) {\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "threadStart  ( t2) {\n" +
//                "\tthreadComplete(t2);\n" +
                "}";

        code = code.replaceAll("\\n", "").replaceAll("\\t", "");
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());

        CodeSnippetElement dagAssertion = new CodeSnippetElement();
        dagAssertion.setCode("");

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode("");

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode("");

        dagAssertion.getNext().add(t1);
        dagAssertion.getNext().add(t2);

        CodeSnippetElement n1 = new CodeSnippetElement();
        n1.setCode("");

        t1.getNext().add(n1);
        t2.getNext().add(n1);


        CodeSnippetElement t3 = new CodeSnippetElement();
        t3.setCode("");

        CodeSnippetElement t4 = new CodeSnippetElement();
        t4.setCode("");

        n1.getNext().add(t3);
        n1.getNext().add(t4);

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("z = x+y;");

        t3.getNext().add(n2);
        t4.getNext().add(n2);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(dagAssertion, codeSnippetElement));
    }

    @Test
    public void mapping() {
        String code = "threadStart(t1) {\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "threadOnEnd(t1,1) {\n" +
                "\tthreadStart(t2) {\n" +
//                "\t\tthreadComplete(t2);\n" +
                "\t}\n" +
                "}\n" +
                "\n" +
                "threadOnEnd(t2,1) {\n" +
                "\tz = x+y;\n" +
                "}";
        code = code.replaceAll("\\n", "").replaceAll("\\t", "");
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());

        CodeSnippetElement dagElement = new CodeSnippetElement();
        dagElement.setCode("");

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode("");
        dagElement.getNext().add(t1);

        CodeSnippetElement n1 = new CodeSnippetElement();
        n1.setCode("");
        t1.getNext().add(n1);

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode("");
        n1.getNext().add(t2);

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("z = x+y;");
        t2.getNext().add(n2);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(dagElement, codeSnippetElement));
    }


    @Test
    public void mappingWithRandomSpace() {
        String code = " threadStart(t1 ) {\n" +
//                "\tthreadComplete(t1);\n" +
                "}\n" +
                "threadOnEnd( t1, 1) {\n" +
                "\tthreadStart (  t2 ) {\n" +
//                "\t\tthreadComplete(t2);\n" +
                "\t}\n" +
                "}\n" +
                "\n" +
                "threadOnEnd(t2, 1) {\n" +
                "\tz = x+y;\n" +
                "}";
        code = code.replaceAll("\\n", "").replaceAll("\\t", "");
        CodeSnippetElement codeSnippetElement = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());

        CodeSnippetElement dagElement = new CodeSnippetElement();
        dagElement.setCode("");

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode("");
        dagElement.getNext().add(t1);

        CodeSnippetElement n1 = new CodeSnippetElement();
        n1.setCode("");
        t1.getNext().add(n1);

        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode("");
        n1.getNext().add(t2);

        CodeSnippetElement n2 = new CodeSnippetElement();
        n2.setCode("z = x+y;");
        t2.getNext().add(n2);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(dagElement, codeSnippetElement));
    }

    @Test
    public void testThreadOnRepeat() {
        String code = "var x:integer;\n" +
                "\n" +
                "\tthreadStart(t1){\n" +
                "\t\t{x}={1};\n" +
                "\t}\n" +
                "\tthreadStart(t2){\n" +
                "\t\t{x}={2};\n" +
                "\t}\n" +
                "\tthreadOnEnd(t1,t2,2){\n" +
                "\t\t{x}={x}+{1};\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n","");
        CodeSnippetElement codeSnippetElementOriginal = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());
        CodeSnippetElement codeSnippetElementAssertion = new CodeSnippetElement();
        codeSnippetElementAssertion.setCode("var x:integer;");

        CodeSnippetElement t1FirstIter = new CodeSnippetElement();
        t1FirstIter.setCode("{x}={1};");
        CodeSnippetElement t2FirstIter = new CodeSnippetElement();
        t2FirstIter.setCode("{x}={2};");

        CodeSnippetElement firstIterEnd = new CodeSnippetElement();
        firstIterEnd.setCode("");

        codeSnippetElementAssertion.getNext().add(t1FirstIter);
        codeSnippetElementAssertion.getNext().add(t2FirstIter);

        t1FirstIter.getNext().add(firstIterEnd);
        t2FirstIter.getNext().add(firstIterEnd);


        //second iteration of the TOR code
        CodeSnippetElement t1SecondIter = new CodeSnippetElement();
        t1SecondIter.setCode("{x}={1};");
        CodeSnippetElement t2SecondIter = new CodeSnippetElement();
        t2SecondIter.setCode("{x}={2};");

        CodeSnippetElement secondIterEnd = new CodeSnippetElement();
        secondIterEnd.setCode("{x}={x}+{1};");

        firstIterEnd.getNext().add(t1SecondIter);
        firstIterEnd.getNext().add(t2SecondIter);

        t1SecondIter.getNext().add(secondIterEnd);
        t2SecondIter.getNext().add(secondIterEnd);


        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(codeSnippetElementAssertion, codeSnippetElementOriginal));
    }

    @Test
    public void testThreadOnRepeatWithRandomSpace() {
        String code = "  var x:integer;\n" +
                "\n" +
                "\t threadStart ( t1 ){\n" +
                "\t\t{x}={1};\n" +
                "\t}\n" +
                "\tthreadStart   (t2){\n" +
                "\t\t{x}={2};\n" +
                "\t}\n" +
                "\t     threadOnEnd(t1,t2,2){\n" +
                "\t\t{x}={x}+{1};\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n","");
        CodeSnippetElement codeSnippetElementOriginal = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());
        CodeSnippetElement codeSnippetElementAssertion = new CodeSnippetElement();
        codeSnippetElementAssertion.setCode("var x:integer;");

        CodeSnippetElement t1FirstIter = new CodeSnippetElement();
        t1FirstIter.setCode("{x}={1};");
        CodeSnippetElement t2FirstIter = new CodeSnippetElement();
        t2FirstIter.setCode("{x}={2};");

        CodeSnippetElement firstIterEnd = new CodeSnippetElement();
        firstIterEnd.setCode("");

        codeSnippetElementAssertion.getNext().add(t1FirstIter);
        codeSnippetElementAssertion.getNext().add(t2FirstIter);

        t1FirstIter.getNext().add(firstIterEnd);
        t2FirstIter.getNext().add(firstIterEnd);


        //second iteration of the TOR code
        CodeSnippetElement t1SecondIter = new CodeSnippetElement();
        t1SecondIter.setCode("{x}={1};");
        CodeSnippetElement t2SecondIter = new CodeSnippetElement();
        t2SecondIter.setCode("{x}={2};");

        CodeSnippetElement secondIterEnd = new CodeSnippetElement();
        secondIterEnd.setCode("{x}={x}+{1};");

        firstIterEnd.getNext().add(t1SecondIter);
        firstIterEnd.getNext().add(t2SecondIter);

        t1SecondIter.getNext().add(secondIterEnd);
        t2SecondIter.getNext().add(secondIterEnd);


        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(codeSnippetElementAssertion, codeSnippetElementOriginal));
    }


    @Test
    public void testThreadOnRepeatWhenThreadInitialisedLater() {
        String code = "var x:integer;\n" +
                "\tthreadOnEnd(t1,t2,2){\n" +
                "\t\t{x}={x}+{1};\n" +
                "\t}\n" +
                "\n" +
                "\tthreadStart(t1){\n" +
                "\t\t{x}={1};\n" +
                "\t}\n" +
                "\tthreadStart(t2){\n" +
                "\t\t{x}={2};\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n","");
        CodeSnippetElement codeSnippetElementOriginal = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());
        CodeSnippetElement codeSnippetElementAssertion = new CodeSnippetElement();
        codeSnippetElementAssertion.setCode("var x:integer;");

        CodeSnippetElement t1FirstIter = new CodeSnippetElement();
        t1FirstIter.setCode("{x}={1};");
        CodeSnippetElement t2FirstIter = new CodeSnippetElement();
        t2FirstIter.setCode("{x}={2};");

        CodeSnippetElement firstIterEnd = new CodeSnippetElement();
        firstIterEnd.setCode("");

        codeSnippetElementAssertion.getNext().add(t1FirstIter);
        codeSnippetElementAssertion.getNext().add(t2FirstIter);

        t1FirstIter.getNext().add(firstIterEnd);
        t2FirstIter.getNext().add(firstIterEnd);


        //second iteration of the TOR code
        CodeSnippetElement t1SecondIter = new CodeSnippetElement();
        t1SecondIter.setCode("{x}={1};");
        CodeSnippetElement t2SecondIter = new CodeSnippetElement();
        t2SecondIter.setCode("{x}={2};");

        CodeSnippetElement secondIterEnd = new CodeSnippetElement();
        secondIterEnd.setCode("{x}={x}+{1};");

        firstIterEnd.getNext().add(t1SecondIter);
        firstIterEnd.getNext().add(t2SecondIter);

        t1SecondIter.getNext().add(secondIterEnd);
        t2SecondIter.getNext().add(secondIterEnd);


        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(codeSnippetElementAssertion, codeSnippetElementOriginal));

    }


    @Test
    public void testThreadOnRepeatWhenThreadInitialisedLaterWithRandomSpace() {
        String code = "var x:integer;\n" +
                "\t     threadOnEnd(t1, t2,  2){\n" +
                "\t\t{x}={x}+{1};\n" +
                "\t}\n" +
                "\n" +
                "\tthreadStart  (  t1){\n" +
                "\t\t{x}={1};\n" +
                "\t}\n" +
                "\t    threadStart(t2){\n" +
                "\t\t{x}={2};\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n","");
        CodeSnippetElement codeSnippetElementOriginal = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(),  new HashMap<>());
        CodeSnippetElement codeSnippetElementAssertion = new CodeSnippetElement();
        codeSnippetElementAssertion.setCode("var x:integer;");

        CodeSnippetElement t1FirstIter = new CodeSnippetElement();
        t1FirstIter.setCode("{x}={1};");
        CodeSnippetElement t2FirstIter = new CodeSnippetElement();
        t2FirstIter.setCode("{x}={2};");

        CodeSnippetElement firstIterEnd = new CodeSnippetElement();
        firstIterEnd.setCode("");

        codeSnippetElementAssertion.getNext().add(t1FirstIter);
        codeSnippetElementAssertion.getNext().add(t2FirstIter);

        t1FirstIter.getNext().add(firstIterEnd);
        t2FirstIter.getNext().add(firstIterEnd);


        //second iteration of the TOR code
        CodeSnippetElement t1SecondIter = new CodeSnippetElement();
        t1SecondIter.setCode("{x}={1};");
        CodeSnippetElement t2SecondIter = new CodeSnippetElement();
        t2SecondIter.setCode("{x}={2};");

        CodeSnippetElement secondIterEnd = new CodeSnippetElement();
        secondIterEnd.setCode("{x}={x}+{1};");

        firstIterEnd.getNext().add(t1SecondIter);
        firstIterEnd.getNext().add(t2SecondIter);

        t1SecondIter.getNext().add(secondIterEnd);
        t2SecondIter.getNext().add(secondIterEnd);


        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(codeSnippetElementAssertion, codeSnippetElementOriginal));

    }

    @Test
    public void testPopulateAllDags() throws CompilationException {
        Mockito.doReturn(codeConverter).when(translateUtil).getNewCodeConverter(Mockito.anyList());
        Mockito.doAnswer(invocationOnMock -> {
            String code = (String) invocationOnMock.getArguments()[0];
            RuleEngineInput ruleEngineInput = (RuleEngineInput) invocationOnMock.getArguments()[1];
            Command command = new Command();
            command.setId(code);
            ruleEngineInput.getCommands().add(command);
            return Collections.singletonList(command);
        }).when(codeConverter).interpret(Mockito.anyString(), Mockito.any(RuleEngineInput.class), Mockito.anyList(),
                Mockito.any(), Mockito.nullable(Map.class), Mockito.nullable(Integer[].class));

        CodeSnippetElement codeSnippetElement = new CodeSnippetElement();
        codeSnippetElement.setCode("code1");

        CodeSnippetElement codeSnippetElementParallel1 = new CodeSnippetElement();
        codeSnippetElementParallel1.setCode("codeParallel1");

        CodeSnippetElement codeSnippetElementParallel2 = new CodeSnippetElement();
        codeSnippetElementParallel2.setCode("codeParallel2");

        CodeSnippetElement terminalCodeSnippet = new CodeSnippetElement();
        terminalCodeSnippet.setCode("terminatingCodeSnippet");

        codeSnippetElementParallel1.getNext().add(terminalCodeSnippet);
        codeSnippetElementParallel2.getNext().add(terminalCodeSnippet);

        codeSnippetElement.getNext().add(codeSnippetElementParallel1);
        codeSnippetElement.getNext().add(codeSnippetElementParallel2);

        DagElement dagElement = translateUtil.populateAllDagElements(codeSnippetElement, new ArrayList<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>(), 0);


        RuleEngineInput dagAssertionREI = new RuleEngineInput();
        Command dagAssertionREICommand = new Command();
        dagAssertionREICommand.setId("code1");
        dagAssertionREI.getCommands().add(dagAssertionREICommand);
        DagElement dagAssertion = new DagElement(dagAssertionREI);
        dagAssertion.setFirstCommandId("code1");

        RuleEngineInput parallel1REI = new RuleEngineInput();
        Command parallel1REICommand = new Command();
        parallel1REICommand.setId("codeParallel1");
        parallel1REI.getCommands().add(parallel1REICommand);
        DagElement parallel1 = new DagElement(parallel1REI);
        parallel1.setFirstCommandId("codeParallel1");


        RuleEngineInput parallel2REI = new RuleEngineInput();
        Command parallel2REICommand = new Command();
        parallel2REICommand.setId("codeParallel2");
        parallel2REI.getCommands().add(parallel2REICommand);
        DagElement parallel2 = new DagElement(parallel2REI);
        parallel2.setFirstCommandId("codeParallel2");


        RuleEngineInput terminatinREI = new RuleEngineInput();
        Command terminatingREICommand = new Command();
        terminatingREICommand.setId("terminatingCodeSnippet");
        terminatinREI.getCommands().add(terminatingREICommand);
        DagElement terminatinREIDag = new DagElement(terminatinREI);
        terminatinREIDag.setFirstCommandId("terminatingCodeSnippet");

        dagAssertion.getNextElements().add(parallel1);
        dagAssertion.getNextElements().add(parallel2);

        parallel1.getNextElements().add(terminatinREIDag);
        parallel2.getNextElements().add(terminatinREIDag);

        Assert.assertTrue(new DagElementDagChecker().checkNodesIfSame(dagAssertion, dagElement));
    }

}

package in.ramanujan.translation;

import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.utils.StringUtils;
import in.ramanujan.translation.codeConverter.utils.TranslateUtil;
import in.ramanujan.translation.dagChecker.CodeSnippetDagChecker;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TranslateUtilTest {

    private final TranslateUtil translateUtil = new TranslateUtil();

    /**
     *          T2
     *        /    \
     *      N1      N3
     *       \     /
     *         T1
     * */
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

    /**
     * threadParallelismCycle(t1, t2, 3) runs its body after EVERY cycle (3 cycles total),
     * unlike threadOnEnd which only runs its body on the last cycle.
     *
     * Expected DAG for 3 cycles:
     *  [preamble]
     *    -> t1_1, t2_1  -> [cycleBody1]
     *                          -> t1_2, t2_2  -> [cycleBody2]
     *                                               -> t1_3, t2_3  -> [cycleBody3]
     */
    @Test
    public void testThreadParallelismCycle() {
        String code = "var x:integer;\n" +
                "\tthreadStart(t1){\n" +
                "\t\tx=1;\n" +
                "\t}\n" +
                "\tthreadStart(t2){\n" +
                "\t\tx=2;\n" +
                "\t}\n" +
                "\tthreadParallelismCycle(t1,t2,3){\n" +
                "\t\tx=x+1;\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n", "");
        CodeSnippetElement result = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(), new HashMap<>());

        CodeSnippetElement assertion = new CodeSnippetElement();
        assertion.setCode("var x:integer;");

        CodeSnippetElement t1Iter1 = new CodeSnippetElement();
        t1Iter1.setCode("x=1;");
        CodeSnippetElement t2Iter1 = new CodeSnippetElement();
        t2Iter1.setCode("x=2;");
        CodeSnippetElement cycleBody1 = new CodeSnippetElement();
        cycleBody1.setCode("x=x+1;");

        assertion.getNext().add(t1Iter1);
        assertion.getNext().add(t2Iter1);
        t1Iter1.getNext().add(cycleBody1);
        t2Iter1.getNext().add(cycleBody1);

        CodeSnippetElement t1Iter2 = new CodeSnippetElement();
        t1Iter2.setCode("x=1;");
        CodeSnippetElement t2Iter2 = new CodeSnippetElement();
        t2Iter2.setCode("x=2;");
        CodeSnippetElement cycleBody2 = new CodeSnippetElement();
        cycleBody2.setCode("x=x+1;");

        cycleBody1.getNext().add(t1Iter2);
        cycleBody1.getNext().add(t2Iter2);
        t1Iter2.getNext().add(cycleBody2);
        t2Iter2.getNext().add(cycleBody2);

        CodeSnippetElement t1Iter3 = new CodeSnippetElement();
        t1Iter3.setCode("x=1;");
        CodeSnippetElement t2Iter3 = new CodeSnippetElement();
        t2Iter3.setCode("x=2;");
        CodeSnippetElement cycleBody3 = new CodeSnippetElement();
        cycleBody3.setCode("x=x+1;");

        cycleBody2.getNext().add(t1Iter3);
        cycleBody2.getNext().add(t2Iter3);
        t1Iter3.getNext().add(cycleBody3);
        t2Iter3.getNext().add(cycleBody3);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(assertion, result));
    }

    @Test
    public void testThreadParallelismCycleWithRandomSpace() {
        String code = "var x:integer;\n" +
                "\tthreadStart ( t1 ){\n" +
                "\t\tx=1;\n" +
                "\t}\n" +
                "\tthreadStart   ( t2 ){\n" +
                "\t\tx=2;\n" +
                "\t}\n" +
                "\t   threadParallelismCycle ( t1, t2, 3 ){\n" +
                "\t\tx=x+1;\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n", "");
        CodeSnippetElement result = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(), new HashMap<>());

        CodeSnippetElement assertion = new CodeSnippetElement();
        assertion.setCode("var x:integer;");

        CodeSnippetElement t1Iter1 = new CodeSnippetElement();
        t1Iter1.setCode("x=1;");
        CodeSnippetElement t2Iter1 = new CodeSnippetElement();
        t2Iter1.setCode("x=2;");
        CodeSnippetElement cycleBody1 = new CodeSnippetElement();
        cycleBody1.setCode("x=x+1;");

        assertion.getNext().add(t1Iter1);
        assertion.getNext().add(t2Iter1);
        t1Iter1.getNext().add(cycleBody1);
        t2Iter1.getNext().add(cycleBody1);

        CodeSnippetElement t1Iter2 = new CodeSnippetElement();
        t1Iter2.setCode("x=1;");
        CodeSnippetElement t2Iter2 = new CodeSnippetElement();
        t2Iter2.setCode("x=2;");
        CodeSnippetElement cycleBody2 = new CodeSnippetElement();
        cycleBody2.setCode("x=x+1;");

        cycleBody1.getNext().add(t1Iter2);
        cycleBody1.getNext().add(t2Iter2);
        t1Iter2.getNext().add(cycleBody2);
        t2Iter2.getNext().add(cycleBody2);

        CodeSnippetElement t1Iter3 = new CodeSnippetElement();
        t1Iter3.setCode("x=1;");
        CodeSnippetElement t2Iter3 = new CodeSnippetElement();
        t2Iter3.setCode("x=2;");
        CodeSnippetElement cycleBody3 = new CodeSnippetElement();
        cycleBody3.setCode("x=x+1;");

        cycleBody2.getNext().add(t1Iter3);
        cycleBody2.getNext().add(t2Iter3);
        t1Iter3.getNext().add(cycleBody3);
        t2Iter3.getNext().add(cycleBody3);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(assertion, result));
    }

    /**
     * Tests the tinyBertTrain.py pattern: threadParallelismCycle handles per-cycle work
     * (body runs every cycle), while a separate threadOnEnd is used on DIFFERENT threads
     * to signal completion of an outer workflow.
     *
     * Here we verify that threadParallelismCycle(t1, t2, 1) with 1 cycle behaves like
     * threadOnEnd(t1, t2, 1) - the body runs exactly once on cycle 1 (the last and only cycle),
     * and there is no re-spawn.
     */
    @Test
    public void testThreadParallelismCycleSingleCycle() {
        String code = "var x:integer;\n" +
                "\tthreadStart(t1){\n" +
                "\t\tx=1;\n" +
                "\t}\n" +
                "\tthreadStart(t2){\n" +
                "\t\tx=2;\n" +
                "\t}\n" +
                "\tthreadParallelismCycle(t1,t2,1){\n" +
                "\t\tx=x+99;\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n", "");
        CodeSnippetElement result = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(), new HashMap<>());

        // With 1 cycle, the DAG should be:
        // preamble -> t1, t2 -> [cycleBody] (no re-spawn)
        CodeSnippetElement assertion = new CodeSnippetElement();
        assertion.setCode("var x:integer;");

        CodeSnippetElement t1 = new CodeSnippetElement();
        t1.setCode("x=1;");
        CodeSnippetElement t2 = new CodeSnippetElement();
        t2.setCode("x=2;");
        CodeSnippetElement cycleBody = new CodeSnippetElement();
        cycleBody.setCode("x=x+99;");

        assertion.getNext().add(t1);
        assertion.getNext().add(t2);
        t1.getNext().add(cycleBody);
        t2.getNext().add(cycleBody);

        Assert.assertTrue(new CodeSnippetDagChecker().checkDag(assertion, result));
    }

    /**
     * Documents why threadParallelismCycle and threadOnEnd must NOT be combined on the
     * same thread set.
     *
     * threadParallelismCycle already handles the final cycle — its body runs after every cycle
     * including the N-th one. Adding threadOnEnd on the same threads causes two problems:
     *
     * 1. Each first-cycle thread receives TWO successors instead of one:
     *    - The threadParallelismCycle body (correct)
     *    - An extra empty join node injected by threadOnEnd's intermediate-iteration handling (wrong)
     *
     * 2. threadOnEnd overwrites map["t1_1"] with its own re-spawn clone, so the final body
     *    is wired to a separate empty-join chain, not to the cycle-body chain. The last
     *    threadParallelismCycle body becomes a dead-end that never leads to the threadOnEnd body.
     *
     * Correct usage: use threadParallelismCycle ALONE for per-cycle work (it covers the last
     * cycle). Use threadOnEnd ALONE if you only want code to run on the final iteration.
     */
    @Test
    public void testThreadParallelismCycleAndThreadOnEndOnSameThreadsShouldNotBeCombined() {
        String code = "var x:integer;\n" +
                "\tthreadStart(t1){\n" +
                "\t\tx=1;\n" +
                "\t}\n" +
                "\tthreadStart(t2){\n" +
                "\t\tx=2;\n" +
                "\t}\n" +
                "\tthreadParallelismCycle(t1,t2,2){\n" +
                "\t\tx=x+10;\n" +
                "\t}\n" +
                "\tthreadOnEnd(t1,t2,2){\n" +
                "\t\tx=x+99;\n" +
                "\t}\n";

        code = code.replaceAll("\\t", "").replaceAll("\\n", "");
        CodeSnippetElement result = translateUtil.getCodeSnippets(code, new HashMap<>(), new HashMap<>(), new HashMap<>());

        Assert.assertNotNull(result);
        Assert.assertEquals("var x:integer;", result.getCode());
        // Two first-cycle threads (t1, t2) are children of the preamble
        Assert.assertEquals(2, result.getNext().size());

        for (CodeSnippetElement thread : result.getNext()) {
            // When combined incorrectly each first-cycle thread ends up with TWO successors:
            //   1. the threadParallelismCycle body ("x=x+10;")
            //   2. an extra empty join node ("") injected by threadOnEnd's intermediate iteration
            // With threadParallelismCycle used ALONE the correct count is 1 (only the cycle body).
            Assert.assertEquals(
                    "Each first-cycle thread must have exactly 2 successors when threadParallelismCycle " +
                            "and threadOnEnd are incorrectly combined on the same threads",
                    2, thread.getNext().size()
            );

            boolean hasCycleBody = false;
            boolean hasEmptyJoinNode = false;
            for (CodeSnippetElement successor : thread.getNext()) {
                if ("x=x+10;".equals(successor.getCode())) hasCycleBody = true;
                if ("".equals(successor.getCode()))        hasEmptyJoinNode = true;
            }
            Assert.assertTrue("One successor must be the cycle body (x=x+10;)", hasCycleBody);
            Assert.assertTrue(
                    "One successor must be the empty join node ('') – the extra unwanted node from threadOnEnd",
                    hasEmptyJoinNode
            );
        }
    }

}

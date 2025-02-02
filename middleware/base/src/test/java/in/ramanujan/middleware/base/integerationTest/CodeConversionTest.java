package in.ramanujan.middleware.base.integerationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.middleware.base.CodeSnippetElement;
import in.ramanujan.middleware.base.DagElement;
import in.ramanujan.middleware.base.codeConverter.CodeConverterLogicFactory;
import in.ramanujan.middleware.base.dagChecker.RuleEngineInputChecker;
import in.ramanujan.middleware.base.integerationTest.assertionRuleEngineInput.*;
import in.ramanujan.middleware.base.pojo.ExtractedCodeAndFunctionCode;
import in.ramanujan.middleware.base.pojo.IndexWrapper;
import in.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.ActualDebugCodeCreator;
import in.ramanujan.middleware.base.spring.SpringConfig;
import in.ramanujan.middleware.base.util.TestHeuristic;
import in.ramanujan.middleware.base.util.TestUtil;
import in.ramanujan.middleware.base.utils.compilation.CompileErrorChecker;
import in.ramanujan.middleware.base.utils.StringUtils;
import in.ramanujan.middleware.base.utils.TranslateUtil;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.While;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.rule.engine.NativeProcessor;
import in.ramanujan.utils.Constants;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


import java.util.*;

/*
https://docs.google.com/document/d/1qaLE-prRLZFUv_UflVEh3QQrtOuVwv8PPdIr28Qjlxo/edit
 */
@RunWith(MockitoJUnitRunner.class)
public class CodeConversionTest {

    private ApplicationContext applicationContext;
    private TranslateUtil translateUtil;
    private CodeConverterLogicFactory codeConverterLogicFactory;
    private CompileErrorChecker compileErrorChecker;

    private int iterations = 0;
    private int iterationsFailed = 0;

    private Logger logger = LoggerFactory.getLogger(CodeConversionTest.class);

    @Before
    public void init() {
        applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        translateUtil = applicationContext.getBean(TranslateUtil.class);
        codeConverterLogicFactory = applicationContext.getBean(CodeConverterLogicFactory.class);
        compileErrorChecker = new CompileErrorChecker();
        compileErrorChecker.setStringUtils(new StringUtils());
        iterations = 0;
        iterationsFailed = 0;

    }

    @Test
    public void test1() throws  Exception{
        String code = "var x:integer;\n" +
                "var z:integer;\n" +
                "{x}={10};\n" +
                "if({x}<{11}){\n" +
                "\t{\n" +
                "\t\t{z}={x};\n" +
                "\t}else{\n" +
                "\t\t{z}={11};\n" +
                "\t}\n" +
                "};\n";

        code = code.replaceAll("\\n","").replaceAll("\\t","");
        String assertRuleEngineInputString = Test1.assertions;
        RuleEngineInput assertionREI = new ObjectMapper().readValue(assertRuleEngineInputString, RuleEngineInput.class);
        Map<String, Command> commandMap = new HashMap<>();
        for(Command command : assertionREI.getCommands()) {
            commandMap.put(command.getId(), command);
        }
        Command firstCommand = commandMap.get(commandMap.get(assertionREI.getCommands().get(0).getNextId()).getNextId());
        firstCommand.setCodeStrPtr(code.indexOf("{x}={10}"));
        commandMap.get(firstCommand.getNextId()).setCodeStrPtr(code.indexOf("if({x}<{11})"));
        commandMap.get(assertionREI.getIfBlocks().get(0).getIfCommand()).setCodeStrPtr(code.indexOf("{z}={x}"));
        commandMap.get(assertionREI.getIfBlocks().get(0).getElseCommandId()).setCodeStrPtr(code.indexOf("{z}={11}"));
        commonTest(code, new ObjectMapper().writeValueAsString(assertionREI));


    }

    private void commonTest(String code, String assertRuleEngineInputString)  throws  Exception {
        RuleEngineInput assertionRuleEngineInput = new ObjectMapper().readValue(assertRuleEngineInputString, RuleEngineInput.class);
        //For running small test, put index = code.length();
        TestUtil.createCombinations(code, code.length(), new IndexWrapper(0), (codeList) -> {
            try {
                String testCode = (String) codeList.get(0);
                compileErrorChecker.checkCompilationEntryPoint(testCode);
                RuleEngineInput ruleEngineInput = getRuleEngineInput(testCode);
                Boolean result = new RuleEngineInputChecker().checkRuleEngineInput(
                        assertionRuleEngineInput, ruleEngineInput, assertionRuleEngineInput.getCommands().get(0).getId(),
                        ruleEngineInput.getCommands().get(0).getId());
                Assert.assertTrue(result);
            } catch (Exception e) {
                Assert.assertTrue(false);
            }

        });
    }

    private void commonTestViaProcessor(String code, TestHeuristic testHeuristic)  throws  Exception {
        //For running small test, put index = code.length();
        TestUtil.createCombinations(code, code.length(), new IndexWrapper(0), (codeList) -> {
            try {
                String testCode = (String) codeList.get(0);
                compileErrorChecker.checkCompilationEntryPoint(testCode);
                RuleEngineInput ruleEngineInput = getRuleEngineInput(testCode);
//                Processor processor = new Processor(ruleEngineInput, ruleEngineInput.getCommands().get(0).getId(), null);
//                Map<String, Object> result = processor.process();
//                processor.endProcess();
                NativeProcessor nativeProcessor = new NativeProcessor();
                nativeProcessor.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
                Map<String, Object> result = nativeProcessor.jniObject;
                List<Object> testHeuristicsArg = new ArrayList<>();
                testHeuristicsArg.add(result);
                testHeuristicsArg.add(ruleEngineInput);
                testHeuristic.test(testHeuristicsArg);

            } catch (Exception e) {
                e.printStackTrace();
                Assert.assertTrue(false);
            }

        });
    }

    @Test
    public void testWhileLoopBigOne() throws Exception {
        String code ="def getSquared(var xPow:integer, var yPow:integer, var ans:integer) {\n" +
                "  if({xPow}<{yPow}) {{\n" +
                "    {ans}={{yPow}-{xPow}};\n" +
                "  }else{\n" +
                "    {ans}={{xPow}-{yPow}};\n" +
                "  }}\n" +
                "}\n" +
                "def getAvg(var arr:array, var originalArr:array, var avgF:integer) {\n" +
                "  var index,ans1,tmpAvg1,tmpAvg2:integer;\n" +
                "  {avgF}={0};\n" +
                "  {index}={0};\n" +
                "  while({index}<{100}) {\n" +
                "      {tmpAvg1}={arr[index]};\n" +
                "      {tmpAvg2}={originalArr[index]};\n" +
                "      exec getSquared(tmpAvg1,tmpAvg2, ans1);\n" +
                "      {avgF}={{avgF}+{ans1}};\n" +
                "      {index}={{index}+{1}};\n" +
                "  }\n" +
                "  {avgF}={{avgF}/{100}};\n" +
                "}\n" +
                "def getTestArr(var xTest:integer, var yTest:integer, var testArrTest:array) {\n" +
                "  var it:integer;\n" +
                "  {it}={0};\n" +
                "  while({it}<{100}) {\n" +
                "    {testArrTest[it]}={{{xTest}*{it}}+{yTest}};\n" +
                "    {it}={{it}+{1}};\n" +
                "  }\n" +
                "}\n" +
                "var train[100]:array;\n" +
                "var i:integer;\n" +
                "{i}={0};\n" +
                "while({i}<{100}) {\n" +
                "  {train[i]}={{{i}*{1.9}}+{33}};\n" +
                "  {i}={{i}+{1}};\n" +
                "}\n" +
                "def mainCode(var train : array, var x1:double, var y1:double) {\n" +
                "  var j,avg,diff1,diff2x,diff2y,tmp:double;\n" +
                "  {j}={0};\n" +
                "  var testArr[100]:array;\n" +
                "  var slope:double;\n" +
                "  var nexty,nextx:double;\n" +
                "  {testArr[1]}={1};\n" +
                "  while({j}<{15000}) {\n" +
                "    exec getTestArr(x1,y1,testArr);\n" +
                "    exec getAvg(testArr, train, diff1);\n" +
                "\n" +
                "    {tmp} ={{x1}+{0.0001}};\n" +
                "    exec getTestArr(tmp,y1,testArr);\n" +
                "    exec getAvg(testArr, train, diff2x);\n" +
                "\n" +
                "    {slope}={{{diff2x}-{diff1}}/{0.0001}};\n" +
                "    {nextx}={{x1}-{{slope}*{0.1}}};\n" +
                "\n" +
                "\n" +
                "    {tmp} ={{y1}+{0.0001}};\n" +
                "    exec getTestArr(x1,tmp,testArr);\n" +
                "    exec getAvg(testArr, train, diff2y);\n" +
                "\n" +
                "    {slope}={{{diff2y}-{diff1}}/{0.0001}};\n" +
                "    {nexty}={{y1}-{{slope}*{0.50}}};\n" +
                "\n" +
                "    {x1}={nextx};\n" +
                "    {y1}={nexty};\n" +
                "\n" +
                "    {j}={{j}+{1}};\n" +
                "  }\n" +
                "}\n" +
                "var x1[100][10],y1[100][10]:array;\n" +
                "{x1[0][0]}={0};\n" +
                "{y1[0][0]} = {0};\n" +
                "var ansX1,ansy1 :double;\n" +
                "{ansX1}={0};\n" +
                "{ansy1}={0};\n" +
                "var iteration[10]:array;\n" +
                "{i}={0};\n" +
                "while({i} < {10}) {\n" +
                "  {iteration[i]}={0};\n" +
                "  {i}={{i}+{1}};\n" +
                "}\n" +
                "\n" +
                "def getBest(var train:array, var best:integer, var x1:array, var y1:array, var iteration:integer) {\n" +
                "  {best}={0};\n" +
                "  var index:integer;\n" +
                "  var bestM:double;\n" +
                "  {bestM}={1000000000};\n" +
                "  {index}={0};\n" +
                "  while({index} < {10}) {\n" +
                "    var testArr[100]:array;\n" +
                "    {testArr[0]}={0};\n" +
                "    var testX1,testY1:double;\n" +
                "    {testX1}={x1[index][iteration]};\n" +
                "    {testY1}={y1[index][iteration]};\n" +
                "    exec getTestArr(testX1,testY1,testArr);\n" +
                "    var avg:double;\n" +
                "    {avg}={0};\n" +
                "    exec getAvg(testArr, train, avg);\n" +
                "    if({avg} < {bestM}) {{\n" +
                "      {bestM} = {avg};\n" +
                "      {best} = {index};\n" +
                "    }}\n" +
                "    {index}={{index}+{1}};\n" +
                "  }\n" +
                "}\n" +
                "def posRun(var thread:integer, var train:array, var x1:array, var y1:array, var iteration :array) {\n" +
                "  var currentIter:integer;\n" +
                "  {currentIter}={iteration[thread]};\n" +
                "  if({currentIter} == {0}) {{\n" +
                "\n" +
                "    {x1[thread][currentIter]}={thread};\n" +
                "    {y1[thread][currentIter]}={thread};\n" +
                "  } else {\n" +
                "    var best :integer;\n" +
                "    {best}={0};\n" +
                "    var thisIter:integer;\n" +
                "    {thisIter}={currentIter};\n" +
                "    {currentIter} = {{currentIter}-{1}};\n" +
                "    exec getBest(train, best, x1, y1, currentIter);\n" +
                "    if({x1[thread][currentIter]} < {x1[best][currentIter]}) {\n" +
                "      {\n" +
                "        {x1[thread][thisIter]} = {{x1[thread][currentIter]}+{{{x1[best][currentIter]}-{x1[thread][currentIter]}}/{2}}};\n" +
                "      } else {\n" +
                "        {x1[thread][thisIter]} = {{x1[thread][currentIter]}-{{{x1[thread][currentIter]}-{x1[best][currentIter]}}/{2}}};\n" +
                "      }\n" +
                "    }\n" +
                "    if({y1[thread][currentIter]} < {y1[best][currentIter]}) {\n" +
                "      {\n" +
                "        {y1[thread][thisIter]} = {{y1[thread][currentIter]}+{{{y1[best][currentIter]}-{y1[thread][currentIter]}}/{2}}};\n" +
                "      } else {\n" +
                "        {y1[thread][thisIter]} = {{y1[thread][currentIter]}-{{{y1[thread][currentIter]}-{y1[best][currentIter]}}/{2}}};\n" +
                "      }\n" +
                "    }\n" +
                "    {currentIter}={thisIter};\n" +
                "  }\n" +
                "}" +
                "\n" +
                "\n" +
                "  var x,y:double;\n" +
                "  {x}={x1[thread][currentIter]};\n" +
                "  {y}={y1[thread][currentIter]};\n" +
                "  exec mainCode(train, x, y);\n" +
                "  {x1[thread][currentIter]}={x};\n" +
                "  {y1[thread][currentIter]}={y};\n" +
                "}\n" +
                "\n" +
                "exec posRun(0, train, x1, y1,iteration);";
        RuleEngineInput ruleEngineInput = getRuleEngineInput(code.replaceAll("\n","").replaceAll("\t",""));
        NativeProcessor nativeProcessor = new NativeProcessor();
        ObjectMapper objectMapper = new ObjectMapper();
        Long timeStart = new Date().toInstant().toEpochMilli();
//        Map<String, Object> result = processor.process();
//        processor.endProcess();
        NativeProcessor processor1 = new NativeProcessor();
        processor1.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        System.out.println("timeTaken" + (new Date().toInstant().toEpochMilli() - timeStart));
    }

    @Test
    public void yetAnotherBigOne() throws Exception {
        String code = "def getSquared(var xPow:integer, var yPow:integer, var ans:integer) {\n" +
                "    if(xPow < yPow) {\n" +
                "        ans = yPow - xPow;\n" +
                "    } else {\n" +
                "        ans = xPow - yPow;\n" +
                "    }\n" +
                "}\n" +
                "    \n" +
                "\n" +
                "def getAvg(var arr:array, var originalArr:array, var avgF:integer) {\n" +
                "  var index,ans1,tmpAvg1,tmpAvg2:integer;\n" +
                "    avgF = 0;\n" +
                "    index = 0;\n" +
                "    while(index < 100) {\n" +
                "        tmpAvg1 = arr[index];\n" +
                "        tmpAvg2 = originalArr[index];\n" +
                "        exec getSquared(tmpAvg1,tmpAvg2, ans1);\n" +
                "        avgF = avgF + ans1;\n" +
                "        index = index + 1;\n" +
                "    }\n" +
                "    avgF = avgF / 100;\n" +
                "}\n" +
                "\n" +
                "def getTestArr(var xTest:integer, var yTest:integer, var testArrTest:array) {\n" +
                "    var it:integer;\n" +
                "    it = 0;\n" +
                "    while(it < 100) {\n" +
                "        testArrTest[it] = xTest * it + yTest;\n" +
                "        it = it + 1;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "var train[100]:array;\n" +
                "var i:integer;\n" +
                "i = 0;\n" +
                "while(i < 100) {\n" +
                "    train[i] = i * 1.9 + 33;\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "\n" +
                "def mainCode(var train : array, var x1:double, var y1:double) {\n" +
                "    var x1,y1,j,avg,diff1,diff2x,diff2y,tmp:double;\n" +
                "    j = 0;\n" +
                "    var testArr[100]:array;\n" +
                "    var slope:double;\n" +
                "    var nexty,nextx:double;\n" +
                "    testArr[1] = 1;\n" +
                "    while(j < 15000) {\n" +
                "        exec getTestArr(x1,y1,testArr);\n" +
                "        exec getAvg(testArr, train, diff1);\n" +
                "\n" +
                "        tmp = x1 + 0.0001;\n" +
                "        exec getTestArr(tmp,y1,testArr);\n" +
                "        exec getAvg(testArr, train, diff2x);\n" +
                "\n" +
                "        slope = (diff2x - diff1) / 0.0001;\n" +
                "        nextx = x1 - slope * 0.1;\n" +
                "\n" +
                "        tmp = y1 + 0.0001;\n" +
                "        exec getTestArr(x1,tmp,testArr);\n" +
                "        exec getAvg(testArr, train, diff2y);\n" +
                "\n" +
                "        slope = (diff2y - diff1) / 0.0001;\n" +
                "        nexty = y1 - slope * 0.50;\n" +
                "\n" +
                "        x1 = nextx;\n" +
                "        y1 = nexty;\n" +
                "\n" +
                "        j = j + 1;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "var x1[100][10],y1[100][10]:array;\n" +
                "x1[0][0] = 0;\n" +
                "y1[0][0] = 0;\n" +
                "var ansX1,ansy1 :double;\n" +
                "ansX1 = 0;\n" +
                "ansy1 = 0;\n" +
                "var iteration[10]:array;\n" +
                "i = 0;\n" +
                "while(i < 10) {\n" +
                "    iteration[i] = 0;\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "\n" +
                "\n" +
                "def getBest(var train:array, var best:integer, var x1:array, var y1:array, var iteration:integer) {\n" +
                "    best = 0;\n" +
                "    var index:integer;\n" +
                "    var bestM:double;\n" +
                "    bestM = 1000000000;\n" +
                "    index = 0;\n" +
                "    while(index < 10) {\n" +
                "        var testArr[100]:array;\n" +
                "        testArr[0] = 0;\n" +
                "        var testX1,testY1:double;\n" +
                "        testX1 = x1[index][iteration];\n" +
                "        testY1 = y1[index][iteration];\n" +
                "        exec getTestArr(testX1,testY1,testArr);\n" +
                "        var avg:double;\n" +
                "        avg = 0;\n" +
                "        exec getAvg(testArr, train, avg);\n" +
                "        if(avg < bestM) {\n" +
                "            bestM = avg;\n" +
                "            best = index;\n" +
                "        }\n" +
                "        index = index + 1;\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "\n" +
                "  def posRun(var thread:integer, var train:array, var x1:array, var y1:array, var iteration :array) {\n" +
                "    var currentIter:integer;\n" +
                "    currentIter = iteration[thread];\n" +
                "    if(currentIter == 0) {\n" +
                "  \n" +
                "      x1[thread][currentIter]=thread;\n" +
                "      y1[thread][currentIter]=thread;\n" +
                "    } else {\n" +
                "      var best :integer;\n" +
                "      best=0;\n" +
                "      var thisIter:integer;\n" +
                "      thisIter=currentIter;\n" +
                "      currentIter = currentIter-1;\n" +
                "      exec getBest(train, best, x1, y1, currentIter);\n" +
                "      if(x1[thread][currentIter] < x1[best][currentIter]) {\n" +
                "        \n" +
                "          x1[thread][thisIter] = x1[thread][currentIter]+(x1[best][currentIter]-x1[thread][currentIter])/2;\n" +
                "        } else {\n" +
                "          x1[thread][thisIter] = x1[thread][currentIter]-(x1[thread][currentIter]-x1[best][currentIter])/2;\n" +
                "       \n" +
                "      }\n" +
                "      if(y1[thread][currentIter] < y1[best][currentIter]) {\n" +
                "          y1[thread][thisIter] = y1[thread][currentIter]+(y1[best][currentIter]-y1[thread][currentIter])/2;\n" +
                "        } else {\n" +
                "          y1[thread][thisIter] = y1[thread][currentIter]-(y1[thread][currentIter]-y1[best][currentIter])/2;\n" +
                "      }\n" +
                "      currentIter=thisIter;\n" +
                "    }\n" +
                "  \n" +
                "\n" +
                "    var x,y:double;\n" +
                "    x=x1[thread][currentIter];\n" +
                "    y=y1[thread][currentIter];\n" +
                "    exec mainCode(train, x, y);\n" +
                "    x1[thread][currentIter]=x;\n" +
                "    y1[thread][currentIter]=y;\n" +
                "  }" +
                "exec posRun(0, train, x1, y1,iteration);";

        RuleEngineInput ruleEngineInput = getRuleEngineInput(code.replaceAll("\n","").replaceAll("\t",""));
//        NativeProcessor nativeProcessor = new NativeProcessor();
//        ObjectMapper objectMapper = new ObjectMapper();
        Long timeStart = new Date().toInstant().toEpochMilli();
//        Map<String, Object> result = processor.process();
//        processor.endProcess();
        NativeProcessor processor1 = new NativeProcessor();
        processor1.process(new ObjectMapper().writeValueAsString(ruleEngineInput), ruleEngineInput.getCommands().get(0).getId());
        System.out.println("timeTaken" + (new Date().toInstant().toEpochMilli() - timeStart));

    }

    private RuleEngineInput getRuleEngineInput(String code) throws Exception {
        Map<String, RuleEngineInput> functionCallsRuleEngineInput = new HashMap<>();
        ExtractedCodeAndFunctionCode extractedCodeAndFunctionCode =translateUtil.extractCodeWithoutAbstractCodeDeclaration(code, functionCallsRuleEngineInput,
                new ActualDebugCodeCreator("", 0));
        CodeSnippetElement firstCodeSnippetElement = translateUtil.getCodeSnippets(extractedCodeAndFunctionCode.getExtractedCode(), new HashMap<>(),
                new HashMap<>(), new HashMap<>());
        DagElement firstDagElement  = translateUtil.populateAllDagElements(firstCodeSnippetElement, new ArrayList<>(),
                functionCallsRuleEngineInput,
                new HashMap<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>(), 0);
        RuleEngineInput ruleEngineInput = firstDagElement.getRuleEngineInput();
        return ruleEngineInput;
    }

    @Test
    public void testArrayConvesion() throws Exception {
        String arrayCode = "var x:integer;\n" +
                "var arr1:array;\n" +
                "{x}={10};\n" +
                "{arr1[x][10]}={10};\n" +
                "{arr1[x][10]}={{arr1[x][10]}+{10}};";


        arrayCode = arrayCode.replaceAll("\\n","").replaceAll("\\t","");
        String assertRuleEngineInput = TestArrayConversion.assertion;
        Map<String, Command> commandMap = new HashMap<>();
        RuleEngineInput assertREI = new ObjectMapper().readValue(assertRuleEngineInput, RuleEngineInput.class);
        for(Command command : assertREI.getCommands()) {
            commandMap.put(command.getId(), command);
        }
        Command firstCommand = commandMap.get(commandMap.get(assertREI.getCommands().get(0).getNextId()).getNextId());
        firstCommand.setCodeStrPtr(arrayCode.indexOf("{x}={10};"));
        Command secondCommand = commandMap.get(firstCommand.getNextId());
        secondCommand.setCodeStrPtr(arrayCode.indexOf("{arr1[x][10]}={10};"));
        Command thirdComand = commandMap.get(secondCommand.getNextId());
        thirdComand.setCodeStrPtr(arrayCode.indexOf("{arr1[x][10]}={{arr1[x][10]}"));
        commonTest(arrayCode, new ObjectMapper().writeValueAsString(assertREI));
    }

    @Test
    public void testFunction() throws Exception{
        String code = "def func(var y:integer){{y}={{y}+{2}};}var x:integer;{x}={1};exec func(x);";
        code = code.replaceAll("\\n","").replaceAll("\\t","");
        String assertRuleEngineInput = TestFunction.assertion;

        RuleEngineInput assertionREIe = new ObjectMapper().readValue(assertRuleEngineInput, RuleEngineInput.class);
        Map<String, Command>  commandMap = new HashMap<>();
        for(Command command : assertionREIe.getCommands()) {
            commandMap.put(command.getId(), command);
        }
        Command firstCommand = commandMap.get(assertionREIe.getCommands().get(0).getNextId());
        firstCommand.setCodeStrPtr(code.indexOf("{x}={1};"));
        Command secondCommand = commandMap.get(firstCommand.getNextId());
        secondCommand.setCodeStrPtr(code.indexOf("exec func(x);"));
        assertionREIe.getFunctionCalls().get(0).setCodeStrPtr(0);
        Command funcCommand = commandMap.get(assertionREIe.getFunctionCalls().get(0).getFirstCommandId());
        funcCommand.setCodeStrPtr(code.indexOf("{y}={{y}+{2}}"));

        commonTest(code, new ObjectMapper().writeValueAsString(assertionREIe));
    }

    @Test
    public void testArrayInFunction() throws Exception{
        String code = "def func(var y:array){{y[0]}={{y[0]}+{1}};}var x:array;{x[0]}={1};exec func(x);";
        code = code.replaceAll("\\n","").replaceAll("\\t","");
        String assertRuleEngineInput = TestArrayInFunction.assertion;
        RuleEngineInput assertionREIe = new ObjectMapper().readValue(assertRuleEngineInput, RuleEngineInput.class);
        Map<String, Command>  commandMap = new HashMap<>();
        for(Command command : assertionREIe.getCommands()) {
            commandMap.put(command.getId(), command);
        }
        Command firstCommand = commandMap.get(assertionREIe.getCommands().get(0).getNextId());
        firstCommand.setCodeStrPtr(code.indexOf("{x[0]}={1}"));
        Command secondCommand = commandMap.get(firstCommand.getNextId());
        secondCommand.setCodeStrPtr(code.indexOf("exec func(x);"));
        assertionREIe.getFunctionCalls().get(0).setCodeStrPtr(0);
        Command funcCommand = commandMap.get(assertionREIe.getFunctionCalls().get(0).getFirstCommandId());
        funcCommand.setCodeStrPtr(code.indexOf("{y[0]}={{y[0]}+{1}}"));

        commonTest(code, new ObjectMapper().writeValueAsString(assertionREIe));
    }

    @Test
    public void testWhile() throws Exception {
        String code = "var x:integer;{x}={1};while({x}<{10}){{x}={{x}+{1}}};{x}={{x}+{10}};";
        code = code.replaceAll("\\n", "").replaceAll("\\t", "");
        String assertRuleEngineInput = TestWhile.assertion;
        RuleEngineInput assertionREIe = new ObjectMapper().readValue(assertRuleEngineInput, RuleEngineInput.class);
        Map<String, Command>  commandMap = new HashMap<>();
        for(Command command : assertionREIe.getCommands()) {
            commandMap.put(command.getId(), command);
        }
        Command firstCommand = commandMap.get(assertionREIe.getCommands().get(0).getNextId());
        firstCommand.setCodeStrPtr(code.indexOf("{x}"));
        While whileRE = assertionREIe.getWhileBlocks().get(0);
        whileRE.setCodeStrPtr(code.indexOf("while"));
        Command whileFirstCommand = commandMap.get(whileRE.getWhileCommandId());
        whileFirstCommand.setCodeStrPtr(code.indexOf("{x}={{x}+{1}}"));
        Command thirdCommand = commandMap.get(commandMap.get(firstCommand.getNextId()).getNextId());
        thirdCommand.setCodeStrPtr(code.indexOf("{x}={{x}+{10}}"));
        commonTest(code, new ObjectMapper().writeValueAsString(assertionREIe));
    }

    @Test
    public void testIfForScopeVariable() throws Exception {
        String code = "var x:integer;\n" +
                "var z:integer;\n" +
                "{x}={10};\n" +
                "if({x}<{11}){\n" +
                "\t{var tmp:integer; {tmp}={100};\n" +
                "\t\t{z}={{x}+{tmp}};\n" +
                "\t}else{\n" +
                "\t\t{z}={11};\n" +
                "\t}\n" +
                "};" +
                "var tmp:integer; {tmp}={200};\n";

        code = code.replaceAll("\\n","").replaceAll("\\t","");
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("z".equalsIgnoreCase(variable.getName())) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 110d);
                }

                if("tmp".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("if") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 100d);
                }

                if("tmp".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("if") == -1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 200d);
                }
            }
        });
    }

    @Test
    public void testWhileForScopeVariable() throws Exception {
        String code = "var x:integer;{x}={1};while({x}<{10}){var j:integer;{j}={1};{x}={{x}+{j}}};var j:integer;{j}={10};{x}={{x}+{j}};";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("x".equalsIgnoreCase(variable.getName())) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 20d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("while") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 1d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("while") == -1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 10d);
                }
            }
        });
    }

    @Test
    public void testArrayInFunctionForScopeVariable() throws Exception {
        String code = "def func(var y:array){var j:integer;{j}={1};{y[0]}={{y[0]}+{j}};}var x[1]:array;var j:integer;{j}={2};" +
                "{x[0]}={j};exec func(x);{x[0]}={{x[0]}+{1}}";
        commonTestViaProcessor(code, (mapList) -> {
            try {
                Map<String, Object> map = (Map) mapList.get(0);
                RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
                for (Variable variable : ruleEngineInput.getVariables()) {
                    if ("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == 0) {
                        Assert.assertTrue(0d == (Double) map.get(variable.getId()));
                    }
                    if ("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == -1) {
                        Assert.assertTrue(2d == (Double) map.get(variable.getId()));
                    }
                }

                for (Array array : ruleEngineInput.getArrays()) {
                    if ("x".equalsIgnoreCase(array.getName())) {
                        Assert.assertTrue(4d == (Double) ((Map<String, Map<String, Object>>) map.get(Constants.arrayIndex))
                                .get(array.getId()).get("0"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();;
                throw e;
            }
        });

        code = "def func(var y:array){while({y[0]} == {2}) {var j: integer; {j}={3};{y[0]}={{y[0]}+{j}};}" +
                "var j:integer;{j}={1};{y[0]}={{y[0]}+{j}};}var x[1]:array;var j:integer;{j}={2};" +
                "{x[0]}={j};exec func(x);";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 0d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("while") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 0d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == -1 &&
                        variable.getId().indexOf("while_") ==-1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 2d);
                }
            }

            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName())) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 6d);
                }
            }
        });

        code = "def func(var y:array){var j:integer;{j}={1};{y[0]}={{y[0]}+{j}};}var x[1]:array;var j:integer;{j}={2};" +
                "{x[0]}={j};exec func(x);{x[0]}={{x[0]}+{j}}";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 0d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 2d);
                }
            }

            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName())) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 5d);
                }
            }
        });

        code = "def func(var y:array){var j:integer;{j}={1};{y[0]}={{y[0]}+{j}};}var x[1]:array;var j:integer;{j}={2};" +
                "{x[0]}={j};exec func(x);{j} = {10};{x[0]}={{x[0]}+{j}}";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 0d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 10d);
                }
            }

            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName())) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 13d);
                }
            }
        });

        code = "def func(var y:array){var j:integer; var x[1]:array; {x[0]}={100};{j}={1};{y[0]}={{y[0]}+{j}};{y[0]}={{y[0]}+{x[0]}};}" +
                "var x[1]:array;var j:integer;{j}={2};" +
                "{x[0]}={j};exec func(x);{j} = {10};{x[0]}={{x[0]}+{j}}";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 0d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 10d);
                }
            }

            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 113d);
                }

//                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") == 0) {
//                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
//                            .get(array.getId()).get("0") == 100d);
//                }
            }
        });

        code = "def func(var y:array){var j:integer; var x[1]:array; {x[0]}={100};{j}={1};{y[0]}={{y[0]}+{j}};{y[0]}={{y[0]}+{x[0]}};}" +
                "var x[1]:array;var j:integer;{j}={2};" +
                "{x[0]}={j};exec func(x);{j} = {10};{x[0]}={{x[0]}+{j}}";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 0d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 10d);
                }
            }

            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 113d);
                }

//                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") == 0) {
//                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
//                            .get(array.getId()).get("0") == 100d);
//                }
            }
        });

        code = "def func(var y:array){var j:integer; var x[1]:array; {x[0]}={100};{j}={1};{y[0]}={{y[0]}+{j}};{y[0]}={{y[0]}+{x[0]}};" +
                "{x[0]}={500};}var x[1]:array;var j:integer;{j}={2};{x[0]}={j};exec func(x);{j} = {10};{x[0]}={{x[0]}+{j}}";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == 0) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 0d);
                }
                if("j".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 10d);
                }
            }

            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") == -1) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 113d);
                }

//                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") == 0) {
//                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
//                            .get(array.getId()).get("0") == 500d);
//                }
            }
        });

        code = "def func1(var y:array, var x:double){{y[0]}={{y[0]}+{1}};{x}={3};}" +
                "def func2(var y:array, var x:double){{y[0]}={{y[0]}+{2}};exec func1(y, x);}" +
                "var x[1]:array; var y:double;{x[0]}={1};{y}={0};exec func2(x,y);";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("y".equalsIgnoreCase(variable.getName())) {
                    Assert.assertTrue((Double) map.get(variable.getId()) == 3d);
                }

            }
            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName())) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 4d);
                }
            }
        });

        code = "def func1(var y:array, var x:array){{y[0]}={{y[0]}+{1}};{x[0]}={3};}" +
                "def func2(var y:array, var x:array){{y[0]}={{y[0]}+{2}};exec func1(y, x);}" +
                "var x[1]:array; var y[1]:array;{x[0]}={1};{y[0]}={0};exec func2(x,y);";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") != 0) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 4d);
                }

                if("y".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") != 0) {
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 3d);
                }
            }
        });

        /*
        * assert that variable on above stack doesnt translate to lower stack vals.
        */
        code = "def func(var x: double) {" +
                "var y: double;{y}={1};" +
                "if({x}<{2}) {{{x}={{x} + {1}};exec func(x);} else{}};" +
                "{x} = {{x}+{y}}; {y} = {5};" +
                "};" +
                "" +
                "" +
                "var x: double;{x}={1};exec func(x);";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Variable variable : ruleEngineInput.getVariables()) {
                if("x".equalsIgnoreCase(variable.getName()) && variable.getId().indexOf("func_func") != 0) {
                    /*second pass of func(x) -> x = 2 -> x=x+y -> x=3; first pass -> x = x+y -> x= 4.*/
                    Assert.assertTrue((Double) map.get(variable.getId()) == 4d);
                }

            }
        });

        /*
        * Above case, buts this time array of one index.
        */
        code = "def func(var x: array) {" +
                "var y[1]: array;{y[0]}={1};" +
                "if({x[0]}<{2}) {{{x[0]}={{x[0]} + {1}};exec func(x);} else{}};" +
                "{x[0]} = {{x[0]}+{y[0]}}; {y[0]} = {5};" +
                "};" +
                "" +
                "" +
                "var x[1]: array;{x[0]}={1};exec func(x[0]);";
        commonTestViaProcessor(code, (mapList) -> {
            Map<String, Object> map = (Map) mapList.get(0);
            RuleEngineInput ruleEngineInput = (RuleEngineInput) mapList.get(1);
            for(Array array : ruleEngineInput.getArrays()) {
                if("x".equalsIgnoreCase(array.getName()) && array.getId().indexOf("func_func") != 0) {
                    /*second pass of func(x) -> x = 2 -> x=x+y -> x=3; first pass -> x = x+y -> x= 4.*/
                    Assert.assertTrue((Double) ((Map<String, Map<String, Object>>)map.get(Constants.arrayIndex))
                            .get(array.getId()).get("0") == 4d);
                }

            }
        });


    }

//    @Test
//    public void testRandom() {
//        String code = "def  func( var  y:array ) {{y [0 ] }={{y[0]}+{1}} ;}var x:array;{x[0]}={1};exec func(x);";
//        code = code.replaceAll("\\n", "").replaceAll("\\t", "");
//        commonTest(code, "{}");
//    }
}

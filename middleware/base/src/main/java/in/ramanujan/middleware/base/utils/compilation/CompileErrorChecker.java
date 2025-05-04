package in.ramanujan.middleware.base.utils.compilation;

import in.ramanujan.middleware.base.pojo.IndexWrapper;
import in.ramanujan.middleware.base.pojo.grammar.CodeContainer;
import in.ramanujan.middleware.base.pojo.grammar.SimpleCodeCommand;
import in.ramanujan.middleware.base.constants.CodeToken;
import in.ramanujan.middleware.base.exception.CompilationException;
import in.ramanujan.middleware.base.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CompileErrorChecker {

    @Autowired
    private StringUtils stringUtils;

    public StringUtils getStringUtils() {
        return stringUtils;
    }

    public void setStringUtils(StringUtils stringUtils) {
        this.stringUtils = stringUtils;
    }

    private CompilationException compilationErrorCreator(int index, List<Integer> newLines, List<Integer> tabs, String message) {
        int lineNumber = getRank(newLines, index), tab = getRank(tabs, index);
        int tabIndex = 0;
        if(tabs.size() != 0) {
            tabIndex = tabs.get(tab);
        }
        CompilationException compilationException = new CompilationException(lineNumber, index - tabIndex, message);
        return compilationException;
    }

    private int getRank(List<Integer> indexes, int indexToBeRanked) {
        int left = 0, right = indexes.size() -1 ;
        while (left < right) {
            int mid = (left + right) / 2;
            if(indexes.get(mid) > indexToBeRanked) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        return  left;
    }

    public void checkCompilationEntryPoint(String code) throws CompilationException {
        List<Integer> newLines = getStringUtils().getAllInstancesOfPattern(code, "\n");
        List<Integer> tabs = getStringUtils().getAllInstancesOfPattern(code, "\t");
        checkCompilation(code, newLines, tabs);
    }

    /*
    * Does basic sanity of the structure. Compilation errors found while compiling shall be thrown from conversion classes
    * */
    private void checkCompilation(String code, List<Integer> newLines, List<Integer> tabs) throws CompilationException {
        checkBrackets(code, newLines, tabs);
        checkIfSignatures(code, newLines, tabs);
        checkWhileSignatures(code, newLines, tabs);
        checkThreadManagements(code, newLines, tabs);
        checkFunctionCalls(code, newLines, tabs);
    }





    protected void checkThreadManagements(String code, List<Integer> newLines, List<Integer> tabs) throws CompilationException {
        Set<String> threadNameSet = new HashSet<>();
        for(int index : getStringUtils().getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.threadStart, '(')) {

            try {
                SimpleCodeCommand simpleCodeCommand = getStringUtils().parseForSimpleCodeCommand(CodeToken.threadStart,
                        code.substring(index), new IndexWrapper(0));
                if(simpleCodeCommand.getArguments() == null || simpleCodeCommand.getArguments().size() == 0) {
                    throw compilationErrorCreator(index, newLines, tabs, "No thread name in threadStart command");
                }
                if(threadNameSet.contains(simpleCodeCommand.getArguments().get(0))) {
                    throw compilationErrorCreator(index, newLines, tabs, "Thread name overloading not allowed " +
                            simpleCodeCommand.getArguments().get(0));
                }
                threadNameSet.add(simpleCodeCommand.getArguments().get(0));
            } catch (CompilationException e) {
                throw e;
            } catch (Exception e) {
                throw compilationErrorCreator(index, newLines, tabs, "Wrong structure for threadStart");
            }
        }

        for(int index : getStringUtils().getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.threadTriggerOnSomeThreadCompleteion,'(')) {
            try {
                SimpleCodeCommand simpleCodeCommand = getStringUtils().parseForSimpleCodeCommand(
                        CodeToken.threadTriggerOnSomeThreadCompleteion, code.substring(index), new IndexWrapper(0));
                if(simpleCodeCommand.getArguments() == null || simpleCodeCommand.getArguments().size() < 2) {
                    throw compilationErrorCreator(index, newLines, tabs, "Insufficient number of arguments in threadOnEnd");
                }
                for(int argumentIndex = 0; argumentIndex < (simpleCodeCommand.getArguments().size() - 1); argumentIndex++) {
                    if(!threadNameSet.contains(simpleCodeCommand.getArguments().get(argumentIndex))) {
                        throw compilationErrorCreator(index, newLines, tabs, "The thread given in argument of " +
                                "threadOnEnd has not been declared");
                    }
                }
                try {
                    Integer.parseInt(simpleCodeCommand.getArguments().get(simpleCodeCommand.getArguments().size() - 1));
                } catch (NumberFormatException e) {
                    throw compilationErrorCreator(index, newLines, tabs, "Iteration is required as last argument in threadOnEnd");
                }
            } catch (CompilationException e) {
                throw e;
            } catch (Exception e) {
                throw compilationErrorCreator(index, newLines, tabs, "Wrong structure for threadOnEnd");
            }
        }
    }

    //Built in methods in native lib:
    private final static Set<String> builtInMethods = new HashSet<String>() {{
        add("RAND");
        add("ABS");
        add("SIN");
        add("COS");
        add("TAN");
        add("ASIN");
        add("ACOS");
        add("ATAN");
        add("PINF");
        add("NINF");
        add("CEIL");
        add("FLOOR");
        add("EXP");
        add("SQRT");
        add("POW");
    }};

    private void checkFunctionCalls(String code, List<Integer> newLines, List<Integer> tabs) throws CompilationException {
        Map<String, List<String>> functionNamesInCompilation = new HashMap<>();
        /*
        * Checking the function declarations
        * */
        for(int index : getStringUtils().getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.functionDef, ' ')) {
            try {
                CodeContainer codeContainer = getStringUtils().parseForCodeContainer(CodeToken.functionDef,
                        code.substring(index), new IndexWrapper(0));
                if(codeContainer.getPlaceHolder() == null) {
                    throw compilationErrorCreator(index, newLines, tabs, "No name of the function while declaring");
                }
                if(functionNamesInCompilation.containsKey(codeContainer.getPlaceHolder())) {
                    throw compilationErrorCreator(index, newLines, tabs, "Functions can not be over-loaded");
                }
                functionNamesInCompilation.put(codeContainer.getPlaceHolder().trim(), codeContainer.getArguments());
            } catch (CompilationException e) {
                throw e;
            } catch (Exception e) {
                throw compilationErrorCreator(index, newLines, tabs, "Structure of Function definition not correct");
            }
        }

        /*
        * Checking the function being executed are in the program
        * */
        for(int index : getStringUtils().getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, CodeToken.functionExec, ' ')) {
            try {
                SimpleCodeCommand simpleCodeCommand = getStringUtils().parseForSimpleCodeCommand(CodeToken.functionExec,
                        code.substring(index), new IndexWrapper(0));
                List<String> argumentsInOriginalFunction = functionNamesInCompilation.get(simpleCodeCommand.getPlaceHolder());
                if(argumentsInOriginalFunction == null && builtInMethods.contains(simpleCodeCommand.getPlaceHolder())) {
                    //TODO: include in the set better, and check for arg size.
                    continue;
                }
                if(argumentsInOriginalFunction == null) {
                    throw compilationErrorCreator(index, newLines, tabs, "Function " + simpleCodeCommand.getPlaceHolder() +
                            " not implemented");
                }
                if(argumentsInOriginalFunction.size() != simpleCodeCommand.getArguments().size()) {
                    throw compilationErrorCreator(index, newLines, tabs, "Function exec call doesnt have same " +
                            "number of required parameters :" + simpleCodeCommand.getPlaceHolder());
                }
            } catch (CompilationException e) {
                throw e;
            } catch (Exception e) {
                throw compilationErrorCreator(index, newLines, tabs, "Structure of Function execution is not correct");
            }
        }
    }

    protected void checkWhileSignatures(String code, List<Integer> newLines, List<Integer> tabs) throws CompilationException{
        for(int index : getStringUtils().getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, "while",'(')) {
            try {
                CodeContainer codeContainer = getStringUtils().parseForCodeContainer("while",
                        code.substring(index), new IndexWrapper(0));
                if(codeContainer.getArguments() == null || codeContainer.getArguments().size() == 0) {
                    throw compilationErrorCreator(index, newLines, tabs, "There are no arguments in while");
                }
            } catch (CompilationException e) {
                throw  e;
            } catch (Exception e) {
                throw compilationErrorCreator(index, newLines, tabs, "Structure of While not correct");
            }
        }
    }

    protected void checkIfSignatures(String code, List<Integer> newLines, List<Integer> tabs) throws CompilationException{
        for(int index : getStringUtils().getAllInstancesOfPatternNotSubstringOfOtherKeyword(code, "if",'(')) {
            try {
                CodeContainer codeContainer = getStringUtils().parseForCodeContainer("if",
                        code.substring(index), new IndexWrapper(0));
                if(codeContainer.getArguments() == null || codeContainer.getArguments().size() == 0) {
                    throw compilationErrorCreator(index, newLines, tabs, "There are no arguments in if");
                }
            } catch (CompilationException e) {
                throw  e;
            } catch (Exception e) {
                throw compilationErrorCreator(index, newLines, tabs, "Structure of If not correct");
            }
        }
    }


    private Boolean checkIfBracketsAreCorrect(String code, int index, String character, String reverseCharacter) {
        Stack<Boolean> stack = new Stack<>();
        stack.push(true);
        index++;
        for(; index < code.length() && !stack.empty(); index++) {
            if(character.equalsIgnoreCase("" + code.charAt(index))) {
                stack.push(true);
                continue;
            }
            if(reverseCharacter.equalsIgnoreCase("" + code.charAt(index))) {
                stack.pop();
            }
        }
        return stack.empty();
    }

    protected void checkBrackets(String code, List<Integer> newLines, List<Integer> tabs) throws CompilationException {
        String message = "Brackets not in sync";
        for(int index : getStringUtils().getAllInstancesOfPattern(code, "{")) {
            if(!checkIfBracketsAreCorrect(code, index, "{", "}")) {
                throw compilationErrorCreator(index, newLines, tabs, message);
            }
        }

        for(int index : getStringUtils().getAllInstancesOfPattern(code, "(")) {
            if(!checkIfBracketsAreCorrect(code, index, "(", ")")) {
                throw compilationErrorCreator(index, newLines, tabs, message);
            }
        }

        for(int index : getStringUtils().getAllInstancesOfPattern(code, "[")) {
            if(!checkIfBracketsAreCorrect(code, index, "[", "]")) {
                throw compilationErrorCreator(index, newLines, tabs, message);
            }
        }
    }
}

package in.ramanujan.middleware.base.utils;

import in.ramanujan.middleware.base.pojo.IndexWrapper;
import in.ramanujan.middleware.base.pojo.grammar.CodeContainer;
import in.ramanujan.middleware.base.pojo.grammar.SimpleCodeCommand;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

@Component
public class StringUtils {
    public List<Integer> getAllInstancesOfPattern(String string, String pattern) {
        List<Integer> instancePositions = new ArrayList<>();
        int lastIndex = 0;
        while(lastIndex != -1) {
            lastIndex = string.indexOf(pattern, lastIndex);
            if(lastIndex >=0) {
                instancePositions.add(lastIndex);
                lastIndex = lastIndex + 1;
            }
        }
        return  instancePositions;
    }

    public List<Integer> getAllInstancesOfPatternNotSubstringOfOtherKeyword(String string, String pattern, Character whatToEndWith) {
        List<Integer> allInstances = getAllInstancesOfPattern(string, pattern);
        List<Integer> result = new ArrayList<>();
        for(Integer index : allInstances) {
            if(!isPartofSomeVariable(string, pattern, index, whatToEndWith)) {
                result.add(index);
            }
        }
        return result;
    }

    private Boolean isPartofSomeVariable(String code, String keyword, int index, Character whatToEndWith) {
        if(keyword == null) {
            return false;
        }
        if(index > 0 && !validateIfNotSuffixOfMethod(code.charAt(index - 1))) {
            return true;
        }
        int tmpIndex = index + keyword.length();
        while(tmpIndex < code.length() && code.charAt(tmpIndex) != whatToEndWith) {
            if(code.charAt(tmpIndex) != ' ') {
                return true;
            }
            tmpIndex++;
        }

        return false;
    }

    private Boolean validateIfNotSuffixOfMethod(Character c) {
        return (!Character.isAlphabetic(c) && !Character.isDigit(c));
    }

    public List<String> getArguments(String commaSeperatedArguments) {
        if(commaSeperatedArguments.length() == 0) {
            return new ArrayList<>();
        }
        String[] arguments = commaSeperatedArguments.split(",");
        List<String> result = new ArrayList<>();
        int size = arguments.length;
        for(int i=0; i<size;i++) {
            result.add(arguments[i].trim());
        }
        return result;
    }

    /**
    * this function is for something like<br>
    * codeCommand placeholder .... (arguements ...) {<br>
    *   <t>code</t><br>
    * }>br>
    * ex:<br>
    * def func(ar1, arg2) {code} : codeCommand is def, placeholder is func<br>
    * if(condition) {code} : codeCommand is if, placeholder is null, arguments is condition<br>
    * while(condition) {code} : codeCommand is while, placeholder is null, arguments is condition<br>
    * threadStart(t1) {code} : codeCommand is threadStart, arguments if t1<br>
    * threadOnEnd(r1,t2,3) {code} : codeCommand is threadOnEnd, <r1,t2,3> is arguments
    * */
    public CodeContainer parseForCodeContainer(String codeCommand, String code, IndexWrapper indexWrapper) {
        CodeContainer codeContainer = new CodeContainer();
        if(code == null) {
            return codeContainer;
        }
        codeContainer = new CodeContainer(parseForSimpleCodeCommand(codeCommand, code, indexWrapper));
        int index = indexWrapper.getIndex();
        String internalCode = "";
        for(; index < code.length() && code.charAt(index) != '{'; index++) {

        }
        index++;
        indexWrapper.setIndex(index);
        internalCode = getInternalCode(code, indexWrapper);
        codeContainer.setCode(internalCode);
        return codeContainer;
    }

    /**
     * input code is something like:<br>
     * if(condition) {code} else {code}<br>
     *
     * the codeContainer should contain like:
     * if(condition) {{code} else {code}}
     *
     * Reason for this is that IfLogicConverter will be able to parse the code easily.
     * */
    public CodeContainer parseForIfCodeContainer(String codeCommand, String code, IndexWrapper indexWrapper) {
        CodeContainer codeContainer = new CodeContainer(parseForSimpleCodeCommand(codeCommand, code, indexWrapper));
        int index = indexWrapper.getIndex();

        String internalCode = "";
        for(; index < code.length() && code.charAt(index) != '{'; index++) {

        }
        index++;
        indexWrapper.setIndex(index);
        internalCode += "{"; // add the opening bracket
        internalCode += getInternalCode(code, indexWrapper);

        int elseNextIndex = code.indexOf("else");
        if(elseNextIndex == -1) {
            internalCode += "}"; // add the closing bracket
            codeContainer.setCode(internalCode);
            return codeContainer;
        }
        for(index = indexWrapper.getIndex(); index < code.length() && index < elseNextIndex; index++) {
            if(code.charAt(index) != ' ') {
                internalCode += "}"; // add the closing bracket
                codeContainer.setCode(internalCode);
                return codeContainer;
            }
        }
        index += 4; // move ahead of else
        for(; index < code.length() && code.charAt(index) != '{'; index++) {

        }
        index++;
        indexWrapper.setIndex(index);
        String internalCodeElseCode = getInternalCode(code, indexWrapper);
        internalCode += "}else{" + internalCodeElseCode + "}";
        codeContainer.setCode(internalCode);
        return codeContainer;
    }

    /*parses the code similar to :
    * {codeSnippet}
    * indexWrapper.getIndex() is the first character after the opening bracket.
    * After completion of the the parser, indexWrapper.getIndex() will give the next index after last end-bracket
    * */
    public String getInternalCode(String code, IndexWrapper indexWrapper) {
        String internalCode ="";
        Stack<Boolean> bracketStack = new Stack<>();
        int index = indexWrapper.getIndex();
        bracketStack.add(true); // for the first opening bracket
        for(; index < code.length(); index++) {
            if(code.charAt(index) == '}') {
                bracketStack.pop();
                if(bracketStack.empty()) {
                    break;
                }
            }
            if(code.charAt(index) == '{') {
                bracketStack.push(true);
            }
            internalCode += code.charAt(index);
        }
        index++;
        indexWrapper.setIndex(index);
        return internalCode;
    }


    /*
    * this function is for something like
    * codeCommand placeholder(arguments)
    * */
    public SimpleCodeCommand parseForSimpleCodeCommand(String codeCommand, String code, IndexWrapper indexWrapper) {
        SimpleCodeCommand codeContainer = new SimpleCodeCommand();
        codeContainer.setCodeCommand(codeCommand);
        int firstOccurenceIndex = code.indexOf(codeCommand);
        if(firstOccurenceIndex == -1) {
            return codeContainer;
        }
        code = code.substring(firstOccurenceIndex + codeCommand.length());
        String placeholder = "";
        int index;
        for(index = 0; index < code.length() && code.charAt(index) != '('; index++) {
            placeholder += code.charAt(index);
        }
        placeholder = placeholder.trim();
        if(placeholder.equalsIgnoreCase("")) {
            placeholder = null;
        }
        codeContainer.setPlaceHolder(placeholder);
        if(index >= code.length()) {
            return codeContainer;
        }
        index++; // move ahead of (
        indexWrapper.setIndex(index);
        String argumentListInString = "";
        for(; index < code.length() && code.charAt(index) != ')'; index++) {
            argumentListInString += code.charAt(index);
        }
        codeContainer.setArguments(getArguments(argumentListInString));
        if(index >= code.length()) {
            return codeContainer;
        }
        index++; // move ahead of )
        indexWrapper.setIndex(index + firstOccurenceIndex + codeCommand.length());
        return codeContainer;
    }

}

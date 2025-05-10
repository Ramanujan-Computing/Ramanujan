package in.ramanujan.translation.codeConverter;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;

import java.util.List;
import java.util.Map;
import java.util.Stack;

public interface CodeConverterLogic {
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput,
                                            CodeConverter codeConverter, List<String> variableScope,
                                            DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException;

    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits);

    default String getInnerCodeBlock(String code, IndexWrapper indexWrapper) {
        String innerCodeBlock = "";
        int len = code.length();
        Stack<String> stack = new Stack<>();
        stack.push("{");
        int i = indexWrapper.getIndex();
        while(i < len && !stack.empty()) {
            innerCodeBlock = innerCodeBlock + code.charAt(i);
            if(i < len) {
                if(code.charAt(i) == '{') {
                    stack.push("{");
                }
                if(code.charAt(i) == '}') {
                    stack.pop();
                }
            }
            i++;
        }
        indexWrapper.setIndex(i);
        innerCodeBlock = innerCodeBlock.substring(0, innerCodeBlock.length()-1);
        return innerCodeBlock;
    }
}

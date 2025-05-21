package in.ramanujan.translation.codeConverter.codeConverterLogicImpl;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.If;
import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogic;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.CodeContainer;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.utils.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IfLogicConverter implements CodeConverterLogic {
    private final StringUtils stringUtils = new StringUtils();
    private final ConditionLogicConverter conditionLogicConverter = new ConditionLogicConverter();

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {
        if (command != null) {
            command.setIfBlocks(ruleEngineInputUnits.getId());
        }
    }

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput,
                                            CodeConverter codeConverter, List<String> variableScope,
                                            DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap,
                                            Integer[] frameVariableCounterId) throws CompilationException {
        IndexWrapper indexWrapper = new IndexWrapper(0);
        try {
            If ifBlock = new If();
            ifBlock.setId("if_" + UUID.randomUUID().toString());
            variableScope.add(ifBlock.getId());
            CodeContainer codeContainer = stringUtils.parseForCodeContainer("if", code, new IndexWrapper(0));

            debugLevelCodeCreator.concat("if (" + codeContainer.getArguments().get(0) + ") {");
            debugLevelCodeCreator.addIndentation();
            debugLevelCodeCreator.nextLine();

            List<Command> commandsInIfChunk = codeConverter.interpret(getIfTrueBlock(codeContainer.getCode(), indexWrapper),
                    ruleEngineInput, variableScope, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId);
            if (commandsInIfChunk.size() > 0) {
                ifBlock.setIfCommand(commandsInIfChunk.get(0).getId());
                debugLevelCodeCreator.decrementIndentation();
                debugLevelCodeCreator.concat("}");
                debugLevelCodeCreator.nextLine();
            }

            final String elseBlock = getIfFalseBlock(codeContainer.getCode(), indexWrapper);
            if (elseBlock != null && !elseBlock.isEmpty()) {
                debugLevelCodeCreator.concat(" else {");
                debugLevelCodeCreator.addIndentation();
                debugLevelCodeCreator.nextLine();
            }
            List<Command> commandsInElseChunk = codeConverter.interpret(elseBlock,
                    ruleEngineInput, variableScope, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId);
            if (commandsInElseChunk.size() > 0) {
                ifBlock.setElseCommandId(commandsInElseChunk.get(0).getId());
                debugLevelCodeCreator.decrementIndentation();
                debugLevelCodeCreator.concat("}");
                debugLevelCodeCreator.nextLine();
            }
            Condition condition = (Condition) (conditionLogicConverter.
                    convertCode(codeContainer.getArguments().get(0), ruleEngineInput, codeConverter, variableScope,
                            new NoConcatImpl(), functionFrameVariableMap, frameVariableCounterId));
            ifBlock.setConditionId(condition.getId());
            ruleEngineInput.getIfBlocks().add(ifBlock);
            variableScope.remove(variableScope.size() - 1);
            return ifBlock;
        } catch (CompilationException e) {
            e.getMessageString().add(code.substring(0, indexWrapper.getIndex()));
            throw e;
        }
    }

    public ConditionLogicConverter getConditionLogicConverter() {
        return conditionLogicConverter;
    }

    private String getIfTrueBlock(String code, IndexWrapper indexWrapper) {
        int index = code.indexOf('{', indexWrapper.getIndex()) + 1;
        indexWrapper.setIndex(index);
        return stringUtils.getInternalCode(code, indexWrapper);
    }

    private String getIfFalseBlock(String code, IndexWrapper indexWrapper) {
        int elseIndex = code.indexOf("else", indexWrapper.getIndex());
        if (elseIndex != -1) {
            int index = code.indexOf("{", elseIndex) + 1;
            indexWrapper.setIndex(index);
            return stringUtils.getInternalCode(code, indexWrapper);
        }
        return "";
    }

    public StringUtils getStringUtils() {
        return stringUtils;
    }
}

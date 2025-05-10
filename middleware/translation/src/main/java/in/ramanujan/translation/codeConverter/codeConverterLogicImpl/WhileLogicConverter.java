package in.ramanujan.translation.codeConverter.codeConverterLogicImpl;

import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogic;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.CodeContainer;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;
import in.ramanujan.translation.codeConverter.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.translation.codeConverter.pojo.IndexWrapper;
import in.ramanujan.translation.codeConverter.utils.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WhileLogicConverter implements CodeConverterLogic {
    private final ConditionLogicConverter conditionLogicConverter = new ConditionLogicConverter();

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {
        if (command != null) {
            command.setWhileId(ruleEngineInputUnits.getId());
        }
    }

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter,
                                            List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator,
                                            Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
        IndexWrapper indexWrapper = new IndexWrapper(0);
        try {
            CodeContainer codeContainer = StringUtils.parseForCodeContainer("while", code, new IndexWrapper(0));

            While whileBlock = new While();
            whileBlock.setId("while_" + UUID.randomUUID().toString());
            variableScope.add(whileBlock.getId());

            String mainCode = codeContainer.getCode();

            debugLevelCodeCreator.concat("while (" + codeContainer.getArguments().get(0) + ") {");
            debugLevelCodeCreator.addIndentation();
            debugLevelCodeCreator.nextLine();

            List<Command> commandsInIfChunk = codeConverter.interpret(mainCode, ruleEngineInput, variableScope, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId);

            Condition condition = (Condition) (conditionLogicConverter.convertCode(codeContainer.getArguments().get(0), ruleEngineInput, codeConverter, variableScope,
                            new NoConcatImpl(), functionFrameVariableMap, frameVariableCounterId));

            whileBlock.setConditionId(condition.getId());
            if (commandsInIfChunk.size() > 0) {
                whileBlock.setWhileCommandId(commandsInIfChunk.get(0).getId());
            }
            ruleEngineInput.getWhileBlocks().add(whileBlock);
            variableScope.remove(variableScope.size() - 1);
            debugLevelCodeCreator.decrementIndentation();
            debugLevelCodeCreator.concat("}");
            debugLevelCodeCreator.nextLine();
            return whileBlock;
        } catch (CompilationException e) {
            e.getMessageString().add(code.substring(0, indexWrapper.getIndex()));
            throw e;
        }
    }

    public ConditionLogicConverter getConditionLogicConverter() {
        return conditionLogicConverter;
    }
}

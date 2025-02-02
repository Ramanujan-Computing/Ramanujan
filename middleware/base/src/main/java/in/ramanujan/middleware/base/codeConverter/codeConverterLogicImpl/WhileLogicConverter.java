package in.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl;

import in.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.ramanujan.middleware.base.codeConverter.CodeConverterLogic;
import in.ramanujan.middleware.base.exception.CompilationException;
import in.ramanujan.middleware.base.pojo.IndexWrapper;
import in.ramanujan.middleware.base.pojo.grammar.CodeContainer;
import in.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;
import in.ramanujan.middleware.base.pojo.grammar.debugLevelCodeCreatorImpl.NoConcatImpl;
import in.ramanujan.middleware.base.utils.StringUtils;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Condition;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WhileLogicConverter implements CodeConverterLogic {

    @Autowired
    private StringUtils stringUtils;

    @Autowired
    private ConditionLogicConverter conditionLogicConverter;

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {
        if(command != null) {
            command.setWhileId(ruleEngineInputUnits.getId());
        }
    }

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter,
                                            List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator,
                                            Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
        IndexWrapper indexWrapper = new IndexWrapper(0);
        try {


            CodeContainer codeContainer = getStringUtils().parseForCodeContainer("while", code, new IndexWrapper(0));

            While whileBlock = new While();
            whileBlock.setId("while_" + UUID.randomUUID().toString());
            variableScope.add(whileBlock.getId());

            String mainCode = codeContainer.getCode();

            debugLevelCodeCreator.concat("while (" + codeContainer.getArguments().get(0) + ") {");
            debugLevelCodeCreator.addIndentation();
            debugLevelCodeCreator.nextLine();

            List<Command> commandsInIfChunk = codeConverter.interpret(mainCode, ruleEngineInput, variableScope, debugLevelCodeCreator, functionFrameVariableMap, frameVariableCounterId);

//            If ifBlock = new If();
//
//            if (commandsInIfChunk.size() > 0) {
//                ifBlock.setIfCommand(commandsInIfChunk.get(0).getId());
//            }
//            ifBlock.setId("if_" + UUID.randomUUID().toString());
//
//            Command ifCommandForWhile = getIfCommandForWhile();
//            ifCommandForWhile.setId("command_" + UUID.randomUUID().toString());
//            ifCommandForWhile.setIfBlocks(ifBlock.getId());
//            if (commandsInIfChunk.size() > 0) {
//                commandsInIfChunk.get(commandsInIfChunk.size() - 1).setNextId(ifCommandForWhile.getId());
//            }
            Condition condition = (Condition) (getConditionLogicConverter().
                    convertCode(codeContainer.getArguments().get(0), ruleEngineInput, codeConverter, variableScope,
                            new NoConcatImpl(), functionFrameVariableMap, frameVariableCounterId));
//            ifBlock.setConditionId(condition.getId());
//            ruleEngineInput.getIfBlocks().add(ifBlock);
//            ruleEngineInput.getCommands().add(ifCommandForWhile);
//
//            return ifBlock;

            whileBlock.setConditionId(condition.getId());
            if(commandsInIfChunk.size() > 0) {
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

    public StringUtils getStringUtils() {
        return stringUtils;
    }
}

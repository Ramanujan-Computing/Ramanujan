package in.ramanujan.middleware.base.dagChecker;

import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayCommand;

import java.util.HashMap;
import java.util.Map;

public class ProcessChecker {
    Map<String, Boolean> inputUnitsSeen;

    public ProcessChecker() {
        inputUnitsSeen = new HashMap<>();
    }

    private void storeInMap(RuleEngineInputUnits ruleEngineInputUnitsDag1, RuleEngineInputUnits ruleEngineInputUnitsDag2,
                            Boolean result) {
        inputUnitsSeen.put(ruleEngineInputUnitsDag1.getId() + "_" + ruleEngineInputUnitsDag2.getId(), result);
        inputUnitsSeen.put(ruleEngineInputUnitsDag2.getId() + "_" + ruleEngineInputUnitsDag1.getId(), result);
    }


    private Boolean getFromMap(RuleEngineInputUnits ruleEngineInputUnitsDag1, RuleEngineInputUnits ruleEngineInputUnitsDag2) {
        if (ruleEngineInputUnitsDag1 == null && ruleEngineInputUnitsDag2 != null) {
            return false;
        }
        if (ruleEngineInputUnitsDag1 != null && ruleEngineInputUnitsDag2 == null) {
            return false;
        }
        if (ruleEngineInputUnitsDag1 == null && ruleEngineInputUnitsDag2 == null) {
            return true;
        }
        Boolean result = inputUnitsSeen.get(ruleEngineInputUnitsDag1.getId() + "_" + ruleEngineInputUnitsDag2.getId());
        return result;
    }


    public Boolean checkProcessing(Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                                   Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2,
                                   String fistCommandIdDag1, String firstCommandIdDag2) {
        return checkCommand((Command) mapBetweenIdAndRuleInputDag1.get(fistCommandIdDag1),
                (Command) mapBetweenIdAndRuleInputDag2.get(firstCommandIdDag2), mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2);
    }

    private Boolean isCodePtrEqual(Command cmd1, Command cmd2) {
        if(cmd1.getCodeStrPtr() == null) {
            if(cmd2.getCodeStrPtr() == -1) {
                return true;
            }
            return false;
        }
        return cmd1.getCodeStrPtr() == cmd1.getCodeStrPtr();
    }

    private Boolean checkCommand(Command commandDag1, Command commandDag2,
                                 Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                                 Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {
        Boolean resultFromMap = getFromMap(commandDag1, commandDag2);
        boolean flag = true;
        if (resultFromMap == null) {
            storeInMap(commandDag1, commandDag2, true);
            if (commandDag1.getIfBlocks() != null) {
                if (!checkIf((If) mapBetweenIdAndRuleInputDag1.get(commandDag1.getIfBlocks()),
                        (If) mapBetweenIdAndRuleInputDag2.get(commandDag2.getIfBlocks()),
                        mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2) ||
                        !isCodePtrEqual(commandDag1, commandDag2)) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }

            if (commandDag1.getConditionId() != null) {
                if (!checkCondition((Condition) mapBetweenIdAndRuleInputDag1.get(commandDag1.getConditionId()),
                        (Condition) mapBetweenIdAndRuleInputDag2.get(commandDag2.getConditionId()),
                        mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2)) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }

            if (commandDag1.getVariableId() != null) {
                if (!checkVariable((Variable) mapBetweenIdAndRuleInputDag1.get(commandDag1.getVariableId()),
                        (Variable) mapBetweenIdAndRuleInputDag2.get(commandDag2.getVariableId()),
                        mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2)) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }
            if (commandDag1.getConstant() != null) {
                if (!checkConstant((Constant) mapBetweenIdAndRuleInputDag1.get(commandDag1.getConstant()),
                        (Constant) mapBetweenIdAndRuleInputDag2.get(commandDag2.getConstant()),
                        mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2)) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }
            if (commandDag1.getOperation() != null) {
                if (!checkOperation((Operation) mapBetweenIdAndRuleInputDag1.get(commandDag1.getOperation()),
                        (Operation) mapBetweenIdAndRuleInputDag2.get(commandDag2.getOperation()),
                        mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2) ||
                        !isCodePtrEqual(commandDag1, commandDag2)) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }
            if(commandDag1.getArrayCommand() != null) {
                if (!checkArrayCommand(
                        commandDag1.getArrayCommand(),
                        commandDag2.getArrayCommand(),
                        mapBetweenIdAndRuleInputDag1,
                        mapBetweenIdAndRuleInputDag2
                )) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }
            if(commandDag1.getWhileId() != null) {
                if(!checkWhile((While) mapBetweenIdAndRuleInputDag1.get(commandDag1.getWhileId()),
                        (While) mapBetweenIdAndRuleInputDag2.get(commandDag2.getWhileId()), mapBetweenIdAndRuleInputDag1,
                        mapBetweenIdAndRuleInputDag2) ||
                        !isCodePtrEqual(commandDag1, commandDag2)) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }
            if(commandDag1.getFunctionCall() != null) {
                if(!checkFunction((FunctionCall) mapBetweenIdAndRuleInputDag1.get(commandDag1.getFunctionCall().getId()),
                        (FunctionCall) mapBetweenIdAndRuleInputDag2.get(commandDag2.getFunctionCall().getId()), mapBetweenIdAndRuleInputDag1,
                        mapBetweenIdAndRuleInputDag2) ||
                        !isCodePtrEqual(commandDag1, commandDag2)) {
                    storeInMap(commandDag1, commandDag2, false);
                    return false;
                }
            }
            Boolean result = checkCommand((Command) mapBetweenIdAndRuleInputDag1.get(commandDag1.getNextId()),
                    (Command) mapBetweenIdAndRuleInputDag2.get(commandDag2.getNextId()), mapBetweenIdAndRuleInputDag1,
                    mapBetweenIdAndRuleInputDag2);
            storeInMap(commandDag1, commandDag2, result);
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean checkVariable(Variable variableDag1, Variable variableDag2,
                                  Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                                  Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {
        Boolean resultFromMap = getFromMap(variableDag1, variableDag2);
        if (resultFromMap == null) {
            Boolean result = variableDag1.getName() != null && variableDag1.getName().equalsIgnoreCase(variableDag2.getName());
            storeInMap(variableDag1, variableDag2, result);
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean checkIf(If ifBlockDag1, If ifBlockDag2,
                            Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                            Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {

        Boolean resultFromMap = getFromMap(ifBlockDag1, ifBlockDag2);
        if (resultFromMap == null) {
            Boolean result = ((checkCondition(
                    (Condition) mapBetweenIdAndRuleInputDag1.get(ifBlockDag1.getConditionId()),
                    (Condition) mapBetweenIdAndRuleInputDag2.get((ifBlockDag2.getConditionId())),
                    mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
            ))
                    &&
                    (checkCommand(
                            (Command) mapBetweenIdAndRuleInputDag1.get(ifBlockDag1.getIfCommand()),
                            (Command) mapBetweenIdAndRuleInputDag2.get(ifBlockDag2.getIfCommand()),
                            mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                    ))
                    &&
                    (checkCommand(
                            (Command) mapBetweenIdAndRuleInputDag1.get(ifBlockDag1.getElseCommandId()),
                            (Command) mapBetweenIdAndRuleInputDag2.get(ifBlockDag2.getElseCommandId()),
                            mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                    )));
            storeInMap(ifBlockDag1, ifBlockDag2, result);
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean checkFunction(FunctionCall functionBlockDag1, FunctionCall functionBlockDag2,
                               Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                               Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {

        Boolean resultFromMap = getFromMap(functionBlockDag1, functionBlockDag2);
        if (resultFromMap == null) {
            Boolean result = (
                    (checkCommand(
                            (Command) mapBetweenIdAndRuleInputDag1.get(functionBlockDag1.getFirstCommandId()),
                            (Command) mapBetweenIdAndRuleInputDag2.get(functionBlockDag2.getFirstCommandId()),
                            mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                    ))
            );
            if(result) {
                if(functionBlockDag1.getArguments().size() != functionBlockDag2.getArguments().size()) {
                    result = false;
                } else {
                    for(int index = 0; index < functionBlockDag1.getArguments().size(); index++) {
                        if(index >= functionBlockDag2.getArguments().size() ||
                                mapBetweenIdAndRuleInputDag1.get(functionBlockDag1.getArguments().get(index)).getClazz() !=
                                mapBetweenIdAndRuleInputDag2.get(functionBlockDag2.getArguments().get(index)).getClazz()) {
                            result = false;
                            break;
                        }
                    }
                }

            }
            storeInMap(functionBlockDag1, functionBlockDag2, result);
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean checkWhile(While whileBlockDag1, While whileBlockDag2,
                            Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                            Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {

        Boolean resultFromMap = getFromMap(whileBlockDag1, whileBlockDag2);
        if (resultFromMap == null) {
            Boolean result = ((checkCondition(
                    (Condition) mapBetweenIdAndRuleInputDag1.get(whileBlockDag1.getConditionId()),
                    (Condition) mapBetweenIdAndRuleInputDag2.get((whileBlockDag2.getConditionId())),
                    mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
            ))
                    &&
                    (checkCommand(
                            (Command) mapBetweenIdAndRuleInputDag1.get(whileBlockDag1.getWhileCommandId()),
                            (Command) mapBetweenIdAndRuleInputDag2.get(whileBlockDag2.getWhileCommandId()),
                            mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                    ))
                    );
            storeInMap(whileBlockDag1, whileBlockDag2, result);
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean checkArrayCommand(ArrayCommand arrayCommand1, ArrayCommand arrayCommand2,
                                      Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                                      Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {
        if(arrayCommand2 == null) {
            return false;
        }
        Array array1 = (Array) mapBetweenIdAndRuleInputDag1.get(arrayCommand1.getArrayId());
        Array array2 = (Array) mapBetweenIdAndRuleInputDag2.get(arrayCommand2.getArrayId());
        Boolean resultFromMap = getFromMap(array1, array2);
        if(resultFromMap == null) {
            Boolean result =  true;
            if(array1.getName() == null || !array1.getName().equalsIgnoreCase(array2.getName())) {
                result = false;
            }
            if(arrayCommand1.getIndex().size() != arrayCommand2.getIndex().size()) {
                result = false;
            }
            int len = arrayCommand1.getIndex().size();
            for(int i=0; result && i < len; i++) {
                result = result && compareIndexes(arrayCommand1.getIndex().get(i), arrayCommand2.getIndex().get(i),
                        mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2);
            }
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean compareIndexes(String indexStr1, String indexStr2, Map<String, RuleEngineInputUnits>
            mapBetweenIdAndRuleInputDag1, Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {
        Object index1 = mapBetweenIdAndRuleInputDag1.get(indexStr1);
        Object index2 = mapBetweenIdAndRuleInputDag2.get(indexStr2);
        if(index1 == null || index2 == null) {
            return false;
        }
        if(index1.getClass() != index2.getClass()) {
            return false;
        }
        if(index1.getClass() == Variable.class) {
            return checkVariable(
                    (Variable) index1, (Variable) index2, mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
            );
        }
        if(index1.getClass() == Constant.class) {
            return checkConstant(
                    (Constant) index1, (Constant) index2, mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
            );
        }
        return true;
    }

    private Boolean checkOperation(Operation operationDag1, Operation operationDag2,
                                   Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                                   Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {
        Boolean resultFromMap = getFromMap(operationDag1, operationDag2);
        if (resultFromMap == null) {
            Boolean result =
                    ((
                            operationDag1.getOperatorType() != null && operationDag1.getOperatorType().
                                    equalsIgnoreCase(operationDag2.getOperatorType())
                    ) &&

                            checkCommand(
                                    (Command) mapBetweenIdAndRuleInputDag1.get(operationDag1.getOperand1()),
                                    (Command) mapBetweenIdAndRuleInputDag2.get(operationDag2.getOperand1()),
                                    mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                            )
                            &&
                            checkCommand(
                                    (Command) mapBetweenIdAndRuleInputDag1.get(operationDag1.getOperand2()),
                                    (Command) mapBetweenIdAndRuleInputDag2.get(operationDag2.getOperand2()),
                                    mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                            ));
            storeInMap(operationDag1, operationDag2, result);
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean checkCondition(Condition conditionDag1, Condition conditionDag2,
                                   Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                                   Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {
        Boolean resultFromMap = getFromMap(conditionDag1, conditionDag2);
        if (resultFromMap == null) {
            Boolean result =
                    ((
                            conditionDag1.getConditionType() != null && conditionDag2.getConditionType().
                                    equalsIgnoreCase(conditionDag2.getConditionType())
                    ) &&

                            checkCommand(
                                    (Command) mapBetweenIdAndRuleInputDag1.get(conditionDag1.getComparisionCommand1()),
                                    (Command) mapBetweenIdAndRuleInputDag2.get(conditionDag2.getComparisionCommand1()),
                                    mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                            )
                            &&
                            checkCommand(
                                    (Command) mapBetweenIdAndRuleInputDag1.get(conditionDag1.getComparisionCommand2()),
                                    (Command) mapBetweenIdAndRuleInputDag2.get(conditionDag2.getComparisionCommand2()),
                                    mapBetweenIdAndRuleInputDag1, mapBetweenIdAndRuleInputDag2
                            ));
            storeInMap(conditionDag1, conditionDag2, result);
            return result;
        } else {
            return resultFromMap;
        }
    }

    private Boolean checkConstant(Constant constantDag1, Constant constantDag2,
                                  Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag1,
                                  Map<String, RuleEngineInputUnits> mapBetweenIdAndRuleInputDag2) {
        Boolean resultFromMap = getFromMap(constantDag1, constantDag2);
        if (resultFromMap == null) {
            if (constantDag1.getValue() != null  && constantDag2.getValue() != null && constantDag1.getDataType() != null
                    && constantDag1.getDataType().equalsIgnoreCase(constantDag2.getDataType()) &&
                    constantDag1.getValue().toString().equalsIgnoreCase(constantDag2.getValue().toString())) {
                storeInMap(constantDag1, constantDag2, true);
                return true;
            } else {
                storeInMap(constantDag1, constantDag2, false);
                return false;
            }
        } else {
            return resultFromMap;
        }
    }
}

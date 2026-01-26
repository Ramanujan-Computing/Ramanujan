//
// Created by pranav on 25/3/24.
//

#ifndef NATIVE_COMMANDRECPP_H
#define NATIVE_COMMANDRECPP_H

#include "Command.hpp"
#include "CommandRE.h"
#include "WhileRE.h"
#include "Variable.hpp"

#include "OperationRE.h"
#include "IfRE.h"
#include "ConstantRE.h"
#include "ArrayCommandRE.h"
#include "dataContainer/VariableRE.h"
#include "CommandProcessing.h"
#include "processingDefinition/ConstantReProcessing.h"
#include "processingDefinition/DefaultProcessing.h"
#include "processingDefinition/IfReProcessing.h"
#include "processingDefinition/WhileReProcessing.h"
#include "processingDefinition/IfReProcessing.h"
#include "processingDefinition/OperationReProcessing.h"
#include "processingDefinition/ConditionReProcessing.h"
#include "processingDefinition/VariableReProcessing.h"
#include "processingDefinition/FunctionReProcessing.h"
#include "processingDefinition/RedefineArrayCommandReProcessing.h"
#include "ReturnRE.h"

#include "DefaultRuleEngineUnits.h"

#include "DebugPoint.h"



CommandRE::CommandRE(Command *command) {
    this->command = command;
}

void CommandRE::chooseRuleEngineUnits(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    whileCommandRE = dynamic_cast<WhileRE *>(getFromMap(map, command->whileId));
    operationCommand = dynamic_cast<OperationRE *>(getFromMap(map, command->operation));
    ifCommandRE = dynamic_cast<IfRE *>(getFromMap(map, command->ifBlocks));
    constantRE = dynamic_cast<ConstantRE *>(getFromMap(map, command->constant));
    variableRE = dynamic_cast<VariableRE *>(getFromMap(map, command->variableId));
    conditionRe = dynamic_cast<ConditionRE *>(getFromMap(map, command->conditionId));
    line = command->codeStrPtr;
    id = command->id;
    
    // Set immediate parent from the command's parent ID
    immediateParent = getFromMap(map, command->immediateParentRuleEngineInputUnitId);

    if (command->functionCall != nullptr) {
        functionCommandRE = GetFunctionCommandRE(command->functionCall, command->functionCall->id, map);
    }

    if (command->arrayCommand != nullptr) {
        ArrayCommand *arrayCommand = command->arrayCommand;
        arrayCommandRE = new ArrayCommandRE(dynamic_cast<ArrayRE *>(getFromMap(map, arrayCommand->arrayId)),
                                            arrayCommand->index, map);
    } else {
        arrayCommandRE = nullptr;
    }

    if(command -> redefineArrayCommand != nullptr) {
        redefineArrayCommandRE = new RedefineArrayCommandRE(command->redefineArrayCommand->arrayId, command->redefineArrayCommand->newDimensions);
    }

    returnStatement = command->returnStatement;

    unit = nullptr;

    // If this command is a return statement, use ReturnRE as the unit
    if (returnStatement) {
        // Build assignment pairs from the command's returnAssignmentPairs
        std::vector<std::pair<std::string, std::string>> assignmentPairs;
        for (const auto& pair : command->returnAssignmentPairs) {
            assignmentPairs.push_back({pair->targetCommandId, pair->sourceCommandId});
        }
        
        returnRE = new ReturnRE({}, assignmentPairs);
        returnRE->immediateParent = immediateParent;
        unit = returnRE;
    }

    if(ifCommandRE != nullptr) {
        unit = ifCommandRE;
    }

    if(whileCommandRE != nullptr) {
        unit = whileCommandRE;
    }

    if(operationCommand != nullptr) {
        unit = operationCommand;
    }

    if(functionCommandRE != nullptr) {
        unit = functionCommandRE;
    }

    if (redefineArrayCommandRE != nullptr) {
        unit = redefineArrayCommandRE;
    }

    if(unit == nullptr) {
        unit = new DefaultRuleEngineUnits();
    }
}

void CommandRE::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    // Resolve nextUnit - if it's a CommandRE, get its internal unit
    RuleEngineInputUnits* nextUnitTemp = getFromMap(map, command->nextId);
    if(nextUnitTemp != nullptr) {
        CommandRE* nextCommandRE = dynamic_cast<CommandRE*>(nextUnitTemp);
        if(nextCommandRE != nullptr) {
            // Get the unit from the next CommandRE
            nextUnit = nextCommandRE->unit;
            if(nextUnit == nullptr) {
                // This shouldn't happen - unit should be set by chooseRuleEngineUnits
                std::cerr << "ERROR: Next CommandRE has nullptr unit! ID: " << command->nextId << std::endl;
                nextUnit = nextCommandRE;  // Fallback to CommandRE itself
            }
        } else {
            nextUnit = nextUnitTemp;
        }
    } else {
        nextUnit = nullptr;
    }

    // Call setFields on units that need it
    if(redefineArrayCommandRE != nullptr) {
        redefineArrayCommandRE->setFields(map);
    }

    if (returnRE != nullptr) {
        returnRE->setFields(map);
    }

    // Set nextUnit in the selected unit
    commandTypeProcessingDefinition = nullptr;

    if(ifCommandRE != nullptr) {
        ifCommandRE->nextUnit = nextUnit;
        commandTypeProcessingDefinition = new IfReProcessing(ifCommandRE);
    }

    if(whileCommandRE != nullptr) {
        whileCommandRE->nextUnit = nextUnit;
        commandTypeProcessingDefinition = new WhileReProcessing(whileCommandRE);
    }

    if(operationCommand != nullptr) {
        operationCommand->nextUnit = nextUnit;
        commandTypeProcessingDefinition = new OperationReProcessing(operationCommand);
    }

    if(functionCommandRE != nullptr) {
        functionCommandRE->nextUnit = nextUnit;
        commandTypeProcessingDefinition = new FunctionReProcessing(functionCommandRE);
    }

    if (redefineArrayCommandRE != nullptr) {
        redefineArrayCommandRE->nextUnit = nextUnit;
        commandTypeProcessingDefinition = new RedefineArrayCommandReProcessing(redefineArrayCommandRE);
    }

    if(unit != nullptr) {
        unit->nextUnit = nextUnit;
    }

    if(commandTypeProcessingDefinition == nullptr) {
        commandTypeProcessingDefinition = new DefaultProcessing();
    }

//
    if(nextUnit != nullptr) {
        nextCommProcessing = new CommandProcessing(nullptr, (nextUnit), nullptr);
        defaultCommandProcessing = nextCommProcessing;
    } else {
        defaultCommandProcessing = new CommandProcessing(nullptr, nullptr, nullptr);
    }
}

RuleEngineInputUnits* CommandRE::process() {
#ifdef DEBUG_BUILD
    debugger->startDebugPoint();
    std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
    debugPoint->setCommandId(id);
    debugPoint->setLine(this->line);
#endif

    // Process the unit and get the next unit from it
    return unit->process();
}

bool CommandRE::evalCondition() {
    return conditionRe->operate();
}

DoublePtr * CommandRE::getVar() {
    if(getDataOperation() != nullptr) {
        return nullptr;
    }
    if(variableRE != nullptr) {
        return (DoublePtr* )variableRE->getVal();
    }

    if(constantRE != nullptr){
        return (DoublePtr* )constantRE->getVal();

    }
}

DataOperation * CommandRE::getDataOperation() {
    if(arrayCommandRE != nullptr) {
        return arrayCommandRE;
    }
    if(operationCommand != nullptr) {
        operationCommand->setCachedOperationFunctioning();
        return operationCommand->get();
    }

    return nullptr;
}


#endif //NATIVE_COMMANDRECPP_H

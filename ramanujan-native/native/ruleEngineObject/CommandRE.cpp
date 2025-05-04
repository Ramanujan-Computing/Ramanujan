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

#include "DefaultRuleEngineUnits.h"

#include "DebugPoint.h"



CommandRE::CommandRE(Command *command) {
    this->command = command;
}

void CommandRE::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    nextCommandRE = dynamic_cast<CommandRE *>(getFromMap(map, command->nextId));
    whileCommandRE = dynamic_cast<WhileRE *>(getFromMap(map, command->whileId));
    operationCommand = dynamic_cast<OperationRE *>(getFromMap(map, command->operation));
    ifCommandRE = dynamic_cast<IfRE *>(getFromMap(map, command->ifBlocks));
    constantRE = dynamic_cast<ConstantRE *>(getFromMap(map, command->constant));
    variableRE = dynamic_cast<VariableRE *>(getFromMap(map, command->variableId));
    conditionRe = dynamic_cast<ConditionRE *>(getFromMap(map, command->conditionId));
    line = command->codeStrPtr;
    id = command->id;

    if (command->functionCall != nullptr) {
        /*
         * TODO:
         * command.functionCall contains the information about how that command is going execute the function which is
         * pointed by functionCall.id;
         * FunctionCallRE has to be created in Processor.
         */
        functionCommandRE = GetFunctionCommandRE(command->functionCall, command->functionCall->id, map);
        functionCommandRE->setFields(map);
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
        redefineArrayCommandRE->setFields(map);
    }

    unit = nullptr;
    commandTypeProcessingDefinition = nullptr;

    if(ifCommandRE != nullptr) {
        unit = ifCommandRE;
        commandTypeProcessingDefinition = new IfReProcessing(ifCommandRE);
    }

    if(whileCommandRE != nullptr) {
        unit = whileCommandRE;
        commandTypeProcessingDefinition = new WhileReProcessing(whileCommandRE);
    }

    if(operationCommand != nullptr) {
        unit = operationCommand;
        commandTypeProcessingDefinition = new OperationReProcessing(operationCommand);
    }

    if(functionCommandRE != nullptr) {
        unit = functionCommandRE;
        commandTypeProcessingDefinition = new FunctionReProcessing(functionCommandRE);
    }

    // Set the correct unit for RedefineArrayCommandRE after all other units, before DefaultRuleEngineUnits
    if (redefineArrayCommandRE != nullptr) {
        unit = redefineArrayCommandRE;
        commandTypeProcessingDefinition = new RedefineArrayCommandReProcessing(redefineArrayCommandRE);
    }

    if(unit == nullptr) {
        unit = new DefaultRuleEngineUnits();
    }

    if(commandTypeProcessingDefinition == nullptr) {
        commandTypeProcessingDefinition = new DefaultProcessing();
    }

//
    if(nextCommandRE != nullptr) {
        nextCommProcessing = new CommandProcessing(nullptr, nextCommandRE, nullptr);
        defaultCommandProcessing = nextCommProcessing;
    } else {
        defaultCommandProcessing = new CommandProcessing(nullptr, nullptr, nullptr);
    }
}

void CommandRE::process() {
}

CommandRE*  CommandRE::get() {
#ifdef DEBUG_BUILD
    debugger->startDebugPoint();
    std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
    debugPoint->setCommandId(id);
    debugPoint->setLine(this->line);
#endif
    unit->process();

    return nextCommandRE;
}

bool CommandRE::evalCondition() {
    return conditionRe->operate();
}

double* CommandRE::getVar() {
    if(getDataOperation() != nullptr) {
        return nullptr;
    }
    if(variableRE != nullptr) {
        return variableRE->getValPtrPtr();
    }

    if(constantRE != nullptr){
        return constantRE->getValPtrPtr();

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

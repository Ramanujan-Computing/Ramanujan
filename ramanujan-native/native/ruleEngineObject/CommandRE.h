//
// Created by pranav on 20/3/24.
//

#ifndef NATIVE_COMMANDRE_H
#define NATIVE_COMMANDRE_H

#include "RuleEngineInputUnits.hpp"
#include "dataContainer/DataContainerRE.h"
#include "CommandTypeProcessingDefinition.h"

class OperationRE;
class IfRE;
class ConstantRE;
class VariableRE;
class ArrayCommandRE;
class WhileRE;
class Command;
class FunctionCommandRE;
class ConditionRE;

class CommandRE : public RuleEngineInputUnits {
private:
    CommandRE* nextCommandRE;
    WhileRE* whileCommandRE;
    OperationRE* operationCommand;
    IfRE* ifCommandRE;
    ConstantRE* constantRE;
    VariableRE* variableRE;
    FunctionCommandRE* functionCommandRE = nullptr;
    ConditionRE* conditionRe;
    ArrayCommandRE* arrayCommandRE = nullptr;
    Command * command;

    RuleEngineInputUnits* unit;

    CommandProcessing* nextCommProcessing;

    CommandTypeProcessingDefinition* commandTypeProcessingDefinition;

    int line;

public:
    //TODO: can we save all variables in an array and variableVal be nothing but just an index to that array?


    // No dataoperation; OperationFunctioning would understand if left arg and right arg are variables / array / or equations.
    // Functions would have demarcation between variable and array in args.
    CommandProcessing* defaultCommandProcessing;
    CommandRE(Command *command);
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override;
    void process() override;
    CommandRE* get();
    DataOperation *getDataOperation();
    double* getVar();
    bool evalCondition();

    void destroy() override {
        if(arrayCommandRE != nullptr) {
            delete arrayCommandRE;
        }
    }
};
#endif //NATIVE_COMMANDRE_H

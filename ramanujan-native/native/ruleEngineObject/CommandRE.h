//
// Created by pranav on 20/3/24.
//

#ifndef NATIVE_COMMANDRE_H
#define NATIVE_COMMANDRE_H

#include "RuleEngineInputUnits.hpp"
#include "dataContainer/DataContainerRE.h"
#include "CommandTypeProcessingDefinition.h"
#include "RedefineArrayCommandRE.h"

class OperationRE;
class IfRE;
class ConstantRE;
class VariableRE;
class ArrayCommandRE;
class WhileRE;
class ReturnRE;
class Command;
class FunctionCommandRE;
class ConditionRE;

class CommandRE : public RuleEngineInputUnits {
private:
    WhileRE* whileCommandRE;
    OperationRE* operationCommand;
    IfRE* ifCommandRE;
    ConstantRE* constantRE;
    VariableRE* variableRE;
    ConditionRE* conditionRe;
    ArrayCommandRE* arrayCommandRE = nullptr;
    ReturnRE* returnRE = nullptr;
    Command * command;
    RedefineArrayCommandRE* redefineArrayCommandRE = nullptr;
    std::vector<std::string> returnValueIds;
    std::vector<std::string> returnTargetIds;

    RuleEngineInputUnits* unit;

    CommandProcessing* nextCommProcessing;

    CommandTypeProcessingDefinition* commandTypeProcessingDefinition;

    int line;

public:
    bool returnStatement = false;  // Made public for If/While blocks to check
    FunctionCommandRE* functionCommandRE = nullptr;
    //TODO: can we save all variables in an array and variableVal be nothing but just an index to that array?


    // No dataoperation; OperationFunctioning would understand if left arg and right arg are variables / array / or equations.
    // Functions would have demarcation between variable and array in args.
    CommandProcessing* defaultCommandProcessing;
    CommandRE(Command *command);
    void chooseRuleEngineUnits(std::unordered_map<std::string, RuleEngineInputUnits *> *map);
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override;
    RuleEngineInputUnits* process() override;
    DataOperation *getDataOperation();
    DoublePtr * getVar();
    bool evalCondition();
    
    RuleEngineInputUnits* getUnit() { return unit; }

    void destroy() override {
        if(arrayCommandRE != nullptr) {
            delete arrayCommandRE;
        }
    }
};
#endif //NATIVE_COMMANDRE_H

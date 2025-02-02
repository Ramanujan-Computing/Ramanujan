//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_OPERATIONRE_H
#define NATIVE_OPERATIONRE_H

#include "RuleEngineInputUnits.hpp"
#include "Operation.hpp"
#include "CommandRE.h"
#include "DataOperation.h"
#include "operatorFunctioning/CachedOperationFunctioning.h"
#include "operatorFunctioning/OperationFunctioning.h"

class OperationRE : public RuleEngineInputUnits {
private:
    Operation* operation;
    CommandRE* operandCommandRE1;
    CommandRE* operandCommandRE2;

    std::string operationType;

    CachedOperationFunctioning* operationFunctioning = nullptr;

public:
    OperationRE(Operation* operation) {
        this->operation = operation;
    }

    void destroy() {
        if(operationFunctioning != nullptr)
            delete operationFunctioning;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        operandCommandRE1 = dynamic_cast<CommandRE *>(getFromMap(map, operation->operand1));
        operandCommandRE2 = dynamic_cast<CommandRE *>(getFromMap(map, operation->operand2));
        operationType = operation->operatorType;
    }

    void process() override;
    CachedOperationFunctioning * get();

    void setCachedOperationFunctioning() {
        if(operationFunctioning != nullptr) {
            return;
        }
        CachedOperationFunctioning *cachedOperationFunctioning = getFunctioning()->getOperationFunctioning(
                operandCommandRE1, operandCommandRE2);
        this->operationFunctioning = cachedOperationFunctioning;;
    }

    OperationFunctioning *getFunctioning();

    bool isAssignType();
};
#endif //NATIVE_OPERATIONRE_H

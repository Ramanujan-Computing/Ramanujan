//
// Created by ramanujan on 25/1/26.
//

#ifndef NATIVE_RETURNOPERATIONRE_H
#define NATIVE_RETURNOPERATIONRE_H

#include "RuleEngineInputUnits.hpp"
#include "ReturnOperation.hpp"
#include "CommandRE.h"
#include "DataOperation.h"
#include "operatorFunctioning/AssignImpl.h"

class ReturnOperationRE : public RuleEngineInputUnits {
private:
    ReturnOperation* returnOperation;
    CommandRE* operandCommandRE1;
    CommandRE* operandCommandRE2;

    DoublePtr *v1 = nullptr;
    DoublePtr *v2 = nullptr;

public:
    ReturnOperationRE(ReturnOperation* returnOperation) {
        this->returnOperation = returnOperation;
    }

    void destroy() override {
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        operandCommandRE1 = dynamic_cast<CommandRE *>(getFromMap(map, returnOperation->operand1));
        operandCommandRE2 = dynamic_cast<CommandRE *>(getFromMap(map, returnOperation->operand2));
        
        // Initialize AssignImplBothVar
        v1 = operandCommandRE1->getVar();
        v2 = operandCommandRE2->getVar();
    }

    CommandRE* process() override {
        v1->value = v2->value;
        
#ifdef DEBUG_BUILD
        debugger->commitDebugPoint();
#endif
        return nextCommandRE;
    }
};

#endif //NATIVE_RETURNOPERATIONRE_H

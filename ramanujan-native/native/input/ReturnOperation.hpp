#ifndef RETURNOPERATION_H
#define RETURNOPERATION_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"

class ReturnOperation : public RuleEngineInputUnit {
    public:
        std::string operatorType, operand1, operand2;

        ReturnOperation(const ramanujan::ReturnOperation* p) {
            this->id = p->id();
            this->operatorType = p->operator_type();
            this->operand1 = p->operand1();
            this->operand2 = p->operand2();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};

#endif

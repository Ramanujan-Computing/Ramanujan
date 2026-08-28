#ifndef OP_H
#define OP_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class Operation : public RuleEngineInputUnit {
    public:
        std::string operatorType, operand1, operand2;

        Operation(const ramanujan::Operation* p) {
            this->id = p->id();
            this->operatorType = p->operator_type();
            this->operand1 = p->operand1();
            this->operand2 = p->operand2();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

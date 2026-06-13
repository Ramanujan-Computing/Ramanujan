#ifndef CONST_H
#define CONST_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class Constant : public RuleEngineInputUnit {
    public:
        std::string dataType;
        double value;

        Constant(const ramanujan::Constant* p) {
            this->id = p->id();
            this->dataType = p->data_type();
            this->value = p->value();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

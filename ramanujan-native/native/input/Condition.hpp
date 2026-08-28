#ifndef COND_H
#define COND_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class Condition : public RuleEngineInputUnit {
    public:
        std::string conditionType;
        std::string comparisionCommand1;
        std::string comparisionCommand2;

        Condition(const ramanujan::Condition* p) {
            this->id = p->id();
            this->conditionType = p->condition_type();
            this->comparisionCommand1 = p->comparision_command1();
            this->comparisionCommand2 = p->comparision_command2();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

#ifndef WHILE_H
#define WHILE_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class While : public RuleEngineInputUnit {
    public:
        std::string conditionId, whileCommandId;

        While(const ramanujan::WhileBlock* p) {
            this->id = p->id();
            this->immediateParentRuleEngineInputUnitId = p->immediate_parent_rule_engine_input_unit_id();
            this->conditionId = p->condition_id();
            this->whileCommandId = p->while_command_id();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

#ifndef IF_H
#define IF_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class If : public RuleEngineInputUnit {
    public:
        std::string conditionId, ifCommand, elseCommand;

        If(const ramanujan::IfBlock* p) {
            this->id = p->id();
            this->immediateParentRuleEngineInputUnitId = p->immediate_parent_rule_engine_input_unit_id();
            this->conditionId = p->condition_id();
            this->ifCommand = p->if_command();
            this->elseCommand = p->else_command();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

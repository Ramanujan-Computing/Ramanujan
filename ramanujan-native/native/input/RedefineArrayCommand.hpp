// RedefineArrayCommand.hpp
#ifndef REDEFINE_ARRAY_COMMAND_H
#define REDEFINE_ARRAY_COMMAND_H

#include <string>
#include <vector>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"
#include "../ruleEngineObject/RedefineArrayCommandRE.h"

class RedefineArrayCommand : public RuleEngineInputUnit {
public:
    std::string arrayId;
    std::vector<std::string> newDimensions;

    RedefineArrayCommand(const ramanujan::RedefineArrayCommand* p) {
        this->id = p->id();
        this->arrayId = p->array_id();
        for (const auto& d : p->new_dimensions()) {
            this->newDimensions.push_back(d);
        }
    }

    RuleEngineInputUnits* getInternalAnalogy() override {
        return new RedefineArrayCommandRE(arrayId, newDimensions);
    }
};

#endif // REDEFINE_ARRAY_COMMAND_H

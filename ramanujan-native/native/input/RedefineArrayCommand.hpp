// RedefineArrayCommand.hpp
#ifndef REDEFINE_ARRAY_COMMAND_H
#define REDEFINE_ARRAY_COMMAND_H

#include <string>
#include <vector>
#include "RuleEngineInputUnit.hpp"
#include <json/json.h>
#include "../ruleEngineObject/RedefineArrayCommandRE.h"

class RedefineArrayCommand : public RuleEngineInputUnit {
public:
    std::string arrayId;
    std::vector<std::string> newDimensions;
    // Optionally, initial values can be added here

    RedefineArrayCommand(Json::Value* value) {
        this->id = (*value)["id"].asString();
        this->arrayId = (*value)["arrayId"].asString();
        for (int i = 0; i < (*value)["newDimensions"].size(); i++) {
            this->newDimensions.push_back((*value)["newDimensions"][i].asString());
        }
        // TODO: Parse initialValue if needed
    }

    RuleEngineInputUnits* getInternalAnalogy() override {
        return new RedefineArrayCommandRE(arrayId, newDimensions);
    }
};

#endif // REDEFINE_ARRAY_COMMAND_H

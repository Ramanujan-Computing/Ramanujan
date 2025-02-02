#ifndef IF_H
#define IF_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include <json/json.h>



class If : public RuleEngineInputUnit {
    public:
        std::string conditionId, ifCommand, elseCommand;

        If(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->conditionId = (*value)["conditionId"].asString();
            this->ifCommand = (*value)["ifCommand"].asString();
            this->elseCommand = (*value)["elseCommandId"].asString();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif


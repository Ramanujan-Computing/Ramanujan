#ifndef WHILE_H
#define WHILE_H

#include <string>
#include "RuleEngineInputUnit.hpp"



class While : public RuleEngineInputUnit {
    public:
        std::string conditionId, whileCommandId;

        While(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->conditionId = (*value)["conditionId"].asString();
            this->whileCommandId = (*value)["whileCommandId"].asString();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif


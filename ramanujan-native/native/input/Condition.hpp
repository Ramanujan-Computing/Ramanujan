#ifndef COND_H
#define COND_H

#include <string>
#include "RuleEngineInputUnit.hpp"



class Condition : public RuleEngineInputUnit {
    public:
        std::string conditionType;
        std::string comparisionCommand1;
        std::string comparisionCommand2;

        Condition(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->conditionType = (*value)["conditionType"].asString();
            this->comparisionCommand1 = (*value)["comparisionCommand1"].asString();
            this->comparisionCommand2 = (*value)["comparisionCommand2"].asString();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

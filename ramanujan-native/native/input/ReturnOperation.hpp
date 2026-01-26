#ifndef RETURNOPERATION_H
#define RETURNOPERATION_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include <json/json.h>

class ReturnOperation : public RuleEngineInputUnit {
    public:
        std::string operatorType, operand1, operand2;

        ReturnOperation(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->operatorType = (*value)["operatorType"].asString();
            this->operand1 = (*value)["operand1"].asString();
            this->operand2 = (*value)["operand2"].asString();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};

#endif

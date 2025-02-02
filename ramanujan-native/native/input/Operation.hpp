#ifndef OP_H
#define OP_H

#include <string>
#include "RuleEngineInputUnit.hpp"



class Operation : public RuleEngineInputUnit {
    public:
        std::string operatorType, operand1, operand2;

        Operation(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->operatorType = (*value)["operatorType"].asString();
            this->operand1 = (*value)["operand1"].asString();
            this->operand2 = (*value)["operand2"].asString();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

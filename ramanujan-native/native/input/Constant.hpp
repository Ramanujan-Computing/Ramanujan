#ifndef CONST_H
#define CONST_H

#include <string>
#include "RuleEngineInputUnit.hpp"



class Constant : public RuleEngineInputUnit {
    public:
        std::string dataType;
        double value;

        Constant(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->dataType = (*value)["dataType"].asString();
            this->value = (*value)["value"].asDouble();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

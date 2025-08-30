#ifndef VARIABLE_H
#define VARIABLE_H

#include <string>
#include <json/value.h>
#include "RuleEngineInputUnit.hpp"



class Variable : public RuleEngineInputUnit {
    public:
    Variable(Json::Value *pValue);

    std::string name, dataType, frameCount;
    double value;
    int localSequence = -1;
    int globalSequence = -1;

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif


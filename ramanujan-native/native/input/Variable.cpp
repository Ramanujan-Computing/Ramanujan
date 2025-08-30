//
// Created by pranav on 26/3/24.
//

#ifndef VARIABLE_CPP
#define VARIABLE_CPP

#include "Variable.hpp"
#include "../ruleEngineObject/dataContainer/VariableRE.h"

RuleEngineInputUnits* Variable::getInternalAnalogy() {
    return new VariableRE(this);
}

Variable::Variable(Json::Value *pValue) {
    id = (*pValue)["id"].asString();
    name = (*pValue)["name"].asString();
    dataType = (*pValue)["dataType"].asString();
    value = (*pValue)["value"].asDouble();
    frameCount = (*pValue)["frameCount"].asString();
    
    // Handle localSequence and globalSequence fields
    if ((*pValue).isMember("localSequence") && !(*pValue)["localSequence"].isNull()) {
        localSequence = (*pValue)["localSequence"].asInt();
    }
    if ((*pValue).isMember("globalSequence") && !(*pValue)["globalSequence"].isNull()) {
        globalSequence = (*pValue)["globalSequence"].asInt();
    }
}


#endif


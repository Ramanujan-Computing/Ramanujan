//
// Created by pranav on 28/3/24.
//

#include "FunctionCall.hpp"
#include "../ruleEngineObject/FunctionCallRE.h"

RuleEngineInputUnits *FunctionCall::getInternalAnalogy() {
    return new
    FunctionCallRE(this);
}
//
// Created by ramanujan on 25/1/26.
//

#ifndef RETURNOPERATION_CPP
#define RETURNOPERATION_CPP

#include "ReturnOperation.hpp"
#include "../ruleEngineObject/ReturnOperationRE.h"

RuleEngineInputUnits* ReturnOperation::getInternalAnalogy() {
    return new ReturnOperationRE(this);
}

#endif

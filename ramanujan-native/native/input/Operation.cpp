//
// Created by pranav on 26/3/24.
//

#ifndef OP_CPP
#define OP_CPP

#include "Operation.hpp"
#include "../ruleEngineObject/OperationRE.h"

RuleEngineInputUnits* Operation::getInternalAnalogy() {
    return new OperationRE(this);
}

#endif

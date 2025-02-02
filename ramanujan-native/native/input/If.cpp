//
// Created by pranav on 28/3/24.
//

#include "If.hpp"

#include "../ruleEngineObject/IfRE.h"

RuleEngineInputUnits *If::getInternalAnalogy() {
    return new IfRE(this);
}
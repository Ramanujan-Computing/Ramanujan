//
// Created by pranav on 28/3/24.
//

#include "While.hpp"
#include "../ruleEngineObject/WhileRE.h"

RuleEngineInputUnits *While::getInternalAnalogy() {
    return new WhileRE(this);
}
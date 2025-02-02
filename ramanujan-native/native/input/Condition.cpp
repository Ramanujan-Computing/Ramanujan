//
// Created by pranav on 28/3/24.
//

#include "Condition.hpp"
#include "../ruleEngineObject/ConditionRE.h"

RuleEngineInputUnits *Condition::getInternalAnalogy() {
    return new ConditionRE(this);
}

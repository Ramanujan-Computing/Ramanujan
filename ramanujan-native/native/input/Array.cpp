//
// Created by pranav on 28/3/24.
//

#include "Array.hpp"
#include "../ruleEngineObject/dataContainer/ArrayRE.h"

RuleEngineInputUnits *Array::getInternalAnalogy() {
    return new ArrayRE(this);
}
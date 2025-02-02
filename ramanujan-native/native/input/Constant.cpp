//
// Created by pranav on 28/3/24.
//

#include "Constant.hpp"
#include "../ruleEngineObject/ConstantRE.h"

RuleEngineInputUnits *Constant::getInternalAnalogy() {
    return new ConstantRE(this);
}

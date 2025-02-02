//
// Created by pranav on 25/3/24.
//


#include "Command.hpp"
#include "../ruleEngineObject/CommandRE.h"

RuleEngineInputUnits* Command::getInternalAnalogy() {
    return new CommandRE(this);
}
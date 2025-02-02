//
// Created by Pranav on 02/04/24.
//

#ifndef NATIVE_CONDITIONFUNCTIONING_H
#define NATIVE_CONDITIONFUNCTIONING_H

#include "../../ruleEngineObject/CommandRE.h"
#include "CachedConditionFunctioning.h"

class ConditionFunctioning {
public:
    virtual CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) = 0;
};
#endif //NATIVE_CONDITIONFUNCTIONING_H

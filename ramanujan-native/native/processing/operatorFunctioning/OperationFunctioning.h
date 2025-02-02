//
// Created by Pranav on 01/04/24.
//

#ifndef NATIVE_OPERATIONFUNCTIONING_H
#define NATIVE_OPERATIONFUNCTIONING_H

#include "../../ruleEngineObject/CommandRE.h"
#include "CachedOperationFunctioning.h"

class OperationFunctioning {
public:
    virtual CachedOperationFunctioning *
    getOperationFunctioning(CommandRE *commandRe1, CommandRE *commandRe2) = 0;
};

#endif //NATIVE_OPERATIONFUNCTIONING_H

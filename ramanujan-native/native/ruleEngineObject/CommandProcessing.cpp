//
// Created by Pranav on 30/03/24.
//

#ifndef NATIVE_COMMANDPROCESSINGCPP_H
#define NATIVE_COMMANDPROCESSINGCPP_H

#include "CommandProcessing.h"
#include "RuleEngineInputUnits.hpp"
#include "DataOperation.h"

CommandProcessing::CommandProcessing(CachedConditionFunctioning *cachedConditionFunctioning,
                                     RuleEngineInputUnits *ruleEngineInputUnits, DataOperation *dataOperation) {
    if (cachedConditionFunctioning != nullptr) {
        this->cachedConditionFunctioning = cachedConditionFunctioning;
    }
    this->ruleEngineInputUnits = ruleEngineInputUnits;
    this->dataOperation = dataOperation;
}


#endif //NATIVE_COMMANDPROCESSINGCPP_H

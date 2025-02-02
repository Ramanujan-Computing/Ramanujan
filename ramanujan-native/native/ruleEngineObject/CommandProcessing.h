//
// Created by Pranav on 30/03/24.
//

#ifndef NATIVE_COMMANDPROCESSING_H
#define NATIVE_COMMANDPROCESSING_H



#include "datastructure/DoubleWrapper.h"
#include "datastructure/BooleanWrapper.h"
#include "DataOperation.h"

class RuleEngineInputUnits;

class CommandProcessing {
    public:
    CommandProcessing(CachedConditionFunctioning *cachedConditionFunctioning,
                      RuleEngineInputUnits *ruleEngineInputUnits, DataOperation *dataOperation);
    CachedConditionFunctioning* cachedConditionFunctioning;
        RuleEngineInputUnits* ruleEngineInputUnits;
        DataOperation* dataOperation;

};



#endif //NATIVE_COMMANDPROCESSING_H

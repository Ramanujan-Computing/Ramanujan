//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_ORIMPL_H
#define NATIVE_ORIMPL_H

#include "CachedConditionFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"
#include "ConditionFunctioning.h"

class OrImplCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    CommandRE* compareWhat;
    CommandRE* compareWith;
    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string* processId;

    OrImplCachedConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) {
        this->compareWhat = compareWhat;
        this->compareWith = compareWith;
        this->processId = processId;
    }

    bool operate() override {
        return compareWhat->evalCondition() || compareWith->evalCondition();
    }
};

class OrImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        return new OrImplCachedConditionFunctioning(compareWhat, compareWith);
    }
};
#endif //NATIVE_ORIMPL_H

//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_NOTIMPL_H
#define NATIVE_NOTIMPL_H

#include "../../ruleEngineObject/CommandRE.h"
#include "CachedConditionFunctioning.h"
#include "ConditionFunctioning.h"

class NotImplCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    CommandRE* compareWhat;
    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string* processId;


    NotImplCachedConditionFunctioning(CommandRE *compareWhat) {
        this->compareWhat = compareWhat;
        this->processId = processId;
    }

    bool operate() override {
        return !compareWhat->evalCondition();
    }
};

class NotImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        return new NotImplCachedConditionFunctioning(compareWhat);
    }
};
#endif //NATIVE_NOTIMPL_H

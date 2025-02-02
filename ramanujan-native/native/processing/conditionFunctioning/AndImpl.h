//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_ANDIMPL_H
#define NATIVE_ANDIMPL_H

#include "CachedConditionFunctioning.h"
#include "ConditionFunctioning.h"
#include "Condition.hpp"

class AndCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    CommandRE* compareWhat;
    CommandRE* compareWith;
    AndCachedConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) {
        this->compareWhat = compareWhat;
        this->compareWith = compareWith;
    }

    bool operate() override {
        return this->compareWhat->evalCondition() && this->compareWith->evalCondition();
    }

};

class AndImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        return new AndCachedConditionFunctioning(compareWhat, compareWith);
    }
};
#endif //NATIVE_ANDIMPL_H

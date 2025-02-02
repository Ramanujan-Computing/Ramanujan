//
// Created by Pranav on 02/04/24.
//
#include "ConditionRE.h"
#include "IfRE.h"
#include "WhileRE.h"
#include "conditionFunctioning/LessThanImpl.h"
#include "conditionFunctioning/AndImpl.h"
#include "conditionFunctioning/OrImpl.h"
#include "conditionFunctioning/NotImpl.h"
#include "conditionFunctioning/LessThanEqualToImpl.h"
#include "conditionFunctioning/GreaterThanImpl.h"
#include "conditionFunctioning/GreaterThanEqualToImpl.h"
#include "conditionFunctioning/NotEqualImpl.h"
#include "conditionFunctioning/IsEqualImpl.h"

void ConditionRE::process() {

}

bool ConditionRE::operate() {
    return conditionFunctioning->operate();
}

void ConditionRE::setCachedConditionFunctioning() {
    if(isCached) {
        return;
    }
    ConditionFunctioning *conditionFunctioning = getFunctioning();
    CachedConditionFunctioning *cachedConditionFunctioning = conditionFunctioning->getConditionFunctioning(
            operandCommandRE1, operandCommandRE2);
    this->conditionFunctioning = cachedConditionFunctioning;

    for(auto whileUser : whileUser) {
        whileUser->conditionFunctioning = cachedConditionFunctioning;
    }

    for(auto ifUser : ifUser) {
        ifUser->conditionFunctioning = cachedConditionFunctioning;
    }

    isCached = true;
}

ConditionFunctioning *ConditionRE::getFunctioning() {
    if (conditionType == "<") {
        return new LessThanImpl();
    }
    if (conditionType == "&&") {
        return new AndImpl();
    }
    if(conditionType == "||") {
        return new OrImpl();
    }
    if(conditionType == "!=") {
        return new NotEqualImpl();
    }
    if(conditionType == "<=") {
        return new LessThanEqualToImpl();
    }
    if(conditionType == ">") {
        return new GreaterThanImpl();
    }
    if(conditionType == ">=") {
        return new GreaterThanEqualToImpl();
    }
    if(conditionType == "not") {
        return new NotImpl();
    }
    if(conditionType == "==") {
        return new IsEqualImpl();
    }

    return nullptr;

}

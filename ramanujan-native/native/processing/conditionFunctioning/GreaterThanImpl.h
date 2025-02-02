//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_GREATERTHANIMPL_H
#define NATIVE_GREATERTHANIMPL_H

#include "CachedConditionFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"
#include "ConditionFunctioning.h"

class GreaterThanCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string* processId;

    DataOperation* compareWhatOp;
    DataOperation* compareWithOp;


    GreaterThanCachedConditionFunctioning(DataOperation* compareWhat, DataOperation* compareWith) {
        this->compareWhatOp = compareWhat;
        this->compareWithOp = compareWith;
    }

    bool operate() override {
        return compareWhatOp->get() > compareWithOp->get();
    }

};

class GreaterThanLeftVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    DataOperation* compareWithOp;
public:
    GreaterThanLeftVar(double* compareWhatValue, DataOperation* compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        return *compareWhatValue > compareWithOp->get();
    }
};

class GreaterThanRightVar : public CachedConditionFunctioning {
    DataOperation* compareWhatOp;
    double * compareWithValue;
public:
    GreaterThanRightVar(DataOperation* compareWhatOp, double* compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() > *compareWithValue;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return val;
    }
};

class GreaterThanBothVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    double * compareWithValue;

public:
    GreaterThanBothVar(double* compareWhatValue, double* compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return *compareWhatValue > *compareWithValue;
    }
};

class GreaterThanImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        DataOperation* compareWhatOp = compareWhat->getDataOperation();
        DataOperation* compareWithOp = compareWith->getDataOperation();

        double* compareWhatValue = compareWhat->getVar();
        double* compareWithValue = compareWith->getVar();

        if(compareWhatValue != nullptr && compareWithValue != nullptr) {
            return new GreaterThanBothVar(compareWhatValue, compareWithValue);
        }
        if(compareWhatValue != nullptr) {
            return new GreaterThanLeftVar(compareWhatValue, compareWithOp);
        }
        if(compareWithValue != nullptr) {
            return new GreaterThanRightVar(compareWhatOp, compareWithValue);
        }

        return new GreaterThanCachedConditionFunctioning(compareWhatOp, compareWithOp);
    }


};
#endif //NATIVE_GREATERTHANIMPL_H

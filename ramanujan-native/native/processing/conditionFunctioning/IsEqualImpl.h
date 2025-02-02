//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_ISEQUALIMPL_H
#define NATIVE_ISEQUALIMPL_H

#include "CachedConditionFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"
#include "ConditionFunctioning.h"

class IsEqualCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string* processId;

    DataOperation* compareWhatOp;
    DataOperation* compareWithOp;

    IsEqualCachedConditionFunctioning(DataOperation* compareWhat, DataOperation* compareWith) {
        this->compareWhatOp = compareWhat;
        this->compareWithOp = compareWith;
    }

    bool operate() override {
        return compareWhatOp->get() == compareWithOp->get();
    }
};

class IsEqualLeftVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    DataOperation* compareWithOp;
public:
    IsEqualLeftVar(double* compareWhatValue, DataOperation* compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        return *compareWhatValue == compareWithOp->get();
    }
};

class IsEqualRightVar : public CachedConditionFunctioning {
    DataOperation* compareWhatOp;
    double * compareWithValue;
public:
    IsEqualRightVar(DataOperation* compareWhatOp, double* compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() == *compareWithValue;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return val;
    }
};

class IsEqualBothVar : public CachedConditionFunctioning {
    double *compareWhatValue;
    double *compareWithValue;
public:
    IsEqualBothVar(double *compareWhatValue, double *compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return *compareWhatValue == *compareWithValue;
    }
};

class IsEqualImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {

        DataOperation* compareWhatOp = compareWhat->getDataOperation();
        DataOperation* compareWithOp = compareWith->getDataOperation();

        double* compareWhatValue = compareWhat->getVar();
        double* compareWithValue = compareWith->getVar();

        if(compareWhatValue != nullptr && compareWithValue != nullptr) {
            return new IsEqualBothVar(compareWhatValue, compareWithValue);
        }
        if(compareWhatValue != nullptr) {
            return new IsEqualLeftVar(compareWhatValue, compareWithOp);
        }
        if(compareWithValue != nullptr) {
            return new IsEqualRightVar(compareWhatOp, compareWithValue);
        }


        return new IsEqualCachedConditionFunctioning(compareWhatOp, compareWithOp);
    }
};
#endif //NATIVE_ISEQUALIMPL_H

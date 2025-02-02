//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_GREATERTHANEQUALTOIMPL_H
#define NATIVE_GREATERTHANEQUALTOIMPL_H

#include "CachedConditionFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"
#include "ConditionFunctioning.h"

class GreaterThanEqualToCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    DataOperation* compareWhatOp;
    DataOperation* compareWithOp;
    GreaterThanEqualToCachedConditionFunctioning(DataOperation* compareWhat, DataOperation* compareWith) {
        this->compareWhatOp = compareWhat;
        this->compareWithOp = compareWith;
    }

    bool operate() override {
        return compareWhatOp->get() >= compareWithOp->get();
    }
};

class GreaterThanEqualLeftVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    DataOperation* compareWithOp;
public:
    GreaterThanEqualLeftVar(double* compareWhatValue, DataOperation* compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        return *compareWhatValue >= compareWithOp->get();
    }
};

class GreaterThanEqualRightVar : public CachedConditionFunctioning {
    DataOperation* compareWhatOp;
    double * compareWithValue;
public:
    GreaterThanEqualRightVar(DataOperation* compareWhatOp, double* compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() >= *compareWithValue;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return val;
    }
};


class GreaterThanEqualBothVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    double * compareWithValue;
public:
    GreaterThanEqualBothVar(double* compareWhatValue, double* compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return *compareWhatValue >= *compareWithValue;
    }
};

class GreaterThanEqualToImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        DataOperation* compareWhatOp = compareWhat->getDataOperation();
        DataOperation* compareWithOp = compareWith->getDataOperation();

        double* compareWhatValue = compareWhat->getVar();
        double* compareWithValue = compareWith->getVar();


        if(compareWhatValue != nullptr && compareWithValue != nullptr) {
            return new GreaterThanEqualBothVar(compareWhatValue, compareWithValue);
        }
        if(compareWhatValue != nullptr) {
            return new GreaterThanEqualLeftVar(compareWhatValue, compareWithOp);
        }
        if(compareWithValue != nullptr) {
            return new GreaterThanEqualRightVar(compareWhatOp, compareWithValue);
        }

        return new GreaterThanEqualToCachedConditionFunctioning(compareWhatOp, compareWithOp);
    }

};
#endif //NATIVE_GREATERTHANEQUALTOIMPL_H

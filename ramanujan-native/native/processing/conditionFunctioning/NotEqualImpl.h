//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_NOTEQUALIMPL_H
#define NATIVE_NOTEQUALIMPL_H

#include "ConditionFunctioning.h"
#include "CachedConditionFunctioning.h"

class NotEqualCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string* processId;

    DataOperation* compareWhatOp;
    DataOperation* compareWithOp;

    NotEqualCachedConditionFunctioning(DataOperation* compareWhat, DataOperation* compareWith) {
        this->compareWhatOp = compareWhat;
        this->compareWithOp = compareWith;
        this->processId = processId;

    }

    bool operate() override {
        return compareWhatOp->get() != compareWithOp->get();
    }
};

class NotEqualLeftVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    DataOperation* compareWithOp;
public:
    NotEqualLeftVar(double* compareWhatValue, DataOperation* compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        return *compareWhatValue != compareWithOp->get();
    }
};

class NotEqualRightVar : public CachedConditionFunctioning {
    DataOperation* compareWhatOp;
    double * compareWithValue;
public:
    NotEqualRightVar(DataOperation* compareWhatOp, double* compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() != *compareWithValue;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return val;
    }
};

class NotEqualBothVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    double * compareWithValue;
public:
    NotEqualBothVar(double* compareWhatValue, double* compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return *compareWhatValue != *compareWithValue;
    }
};

class NotEqualImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        DataOperation* compareWhatOp = compareWhat->getDataOperation();
        DataOperation* compareWithOp = compareWith->getDataOperation();

        double* compareWhatValue = compareWhat->getVar();
        double* compareWithValue = compareWith->getVar();

        if(compareWhatValue != nullptr && compareWithValue != nullptr) {
            return new NotEqualBothVar(compareWhatValue, compareWithValue);
        }
        if(compareWhatValue != nullptr) {
            return new NotEqualLeftVar(compareWhatValue, compareWithOp);
        }
        if(compareWithValue != nullptr) {
            return new NotEqualRightVar(compareWhatOp, compareWithValue);
        }

        return new NotEqualCachedConditionFunctioning(compareWhatOp, compareWithOp);
    }

};
#endif //NATIVE_NOTEQUALIMPL_H

//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_LESSTHANIMPL_H
#define NATIVE_LESSTHANIMPL_H

#include "ConditionFunctioning.h"

class LessThanCachedConditionFunctioning : public CachedConditionFunctioning {
public:

    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string *processId;

    DataOperation *compareWhatOp;
    DataOperation *compareWithOp;

    LessThanCachedConditionFunctioning(DataOperation *compareWhat, DataOperation *compareWith) {
        this->compareWhatOp = compareWhat;
        this->compareWithOp = compareWith;
    }

    bool operate() override {
        return compareWhatOp->get() < compareWithOp->get();
    }
};

class LessThanLeftVar : public CachedConditionFunctioning {
    double *compareWhatValue;
    DataOperation *compareWithOp;
public:
    LessThanLeftVar(double *compareWhatValue, DataOperation *compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        return *compareWhatValue < compareWithOp->get();
    }
};

class LessThanRightVar : public CachedConditionFunctioning {
    DataOperation *compareWhatOp;
    double *compareWithValue;
public:
    LessThanRightVar(DataOperation *compareWhatOp, double *compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() < *compareWithValue;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return val;
    }
};

class LessThanBothVar : public CachedConditionFunctioning {
    double *compareWhatValue;
    double *compareWithValue;
public:
    LessThanBothVar(double *compareWhatValue, double *compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return *compareWhatValue < *compareWithValue;
    }
};


class LessThanImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {

        DataOperation *compareWhatOp = compareWhat->getDataOperation();
        DataOperation *compareWithOp = compareWith->getDataOperation();

        double *compareWhatValue = compareWhat->getVar();
        double *compareWithValue = compareWith->getVar();


        if (compareWhatValue != nullptr && compareWithValue != nullptr) {
            return new LessThanBothVar(compareWhatValue, compareWithValue);
        }
        if(compareWhatValue != nullptr) {
            return new LessThanLeftVar(compareWhatValue, compareWithOp);
        }
        if(compareWithValue != nullptr) {
            return new LessThanRightVar(compareWhatOp, compareWithValue);
        }

        return new LessThanCachedConditionFunctioning(compareWhatOp, compareWithOp);
    }
};

#endif //NATIVE_LESSTHANIMPL_H

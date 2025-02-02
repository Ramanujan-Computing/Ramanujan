//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_LESSTHANEQUALTOIMPL_H
#define NATIVE_LESSTHANEQUALTOIMPL_H
#include "CachedConditionFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"
#include "ConditionFunctioning.h"

class LessThanEqualToCachedConditionFunctioning : public CachedConditionFunctioning {
public:
    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string* processId;

    DataOperation* compareWhatOp;
    DataOperation* compareWithOp;

    LessThanEqualToCachedConditionFunctioning(DataOperation* compareWhat, DataOperation* compareWith) {
        this->compareWhatOp = compareWhat;
        this->compareWithOp = compareWith;
    }

    bool operate() override {
        return compareWhatOp->get() <= compareWithOp->get();
    }
};

class LessThanEqualLeftVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    DataOperation* compareWithOp;
public:
    LessThanEqualLeftVar(double* compareWhatValue, DataOperation* compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        return *compareWhatValue <= compareWithOp->get();
    }
};

class LessThanEqualRightVar : public CachedConditionFunctioning {
    DataOperation* compareWhatOp;
    double * compareWithValue;
public:
    LessThanEqualRightVar(DataOperation* compareWhatOp, double* compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() <= *compareWithValue;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return val;
    }

};

class LessThanEqualBothVar : public CachedConditionFunctioning {
    double * compareWhatValue;
    double * compareWithValue;
public:
    LessThanEqualBothVar(double* compareWhatValue, double* compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue);
        return *compareWhatValue <= *compareWithValue;
    }
};

class LessThanEqualToImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        DataOperation* compareWhatOp = compareWhat->getDataOperation();
        DataOperation* compareWithOp = compareWith->getDataOperation();

        double* compareWhatValue = compareWhat->getVar();
        double* compareWithValue = compareWith->getVar();

        if (compareWhatValue != nullptr && compareWithValue != nullptr) {
            return new LessThanEqualBothVar(compareWhatValue, compareWithValue);
        } else if (compareWhatValue != nullptr) {
            return new LessThanEqualLeftVar(compareWhatValue, compareWithOp);
        } else if (compareWithValue != nullptr) {
            return new LessThanEqualRightVar(compareWhatOp, compareWithValue);
        }



        return new LessThanEqualToCachedConditionFunctioning(compareWhatOp, compareWithOp);
    }
};
#endif //NATIVE_LESSTHANEQUALTOIMPL_H

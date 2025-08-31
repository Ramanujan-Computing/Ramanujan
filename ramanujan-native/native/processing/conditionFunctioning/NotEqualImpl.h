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
    DoublePtr * compareWhatValue;
    DataOperation* compareWithOp;
public:
    NotEqualLeftVar(DoublePtr* compareWhatValue, DataOperation* compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue->value);
        return *compareWhatValue->value != compareWithOp->get();
    }
};

class NotEqualRightVar : public CachedConditionFunctioning {
    DataOperation* compareWhatOp;
    DoublePtr * compareWithValue;
public:
    NotEqualRightVar(DataOperation* compareWhatOp, DoublePtr* compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() != *compareWithValue->value;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue->value);
        return val;
    }
};

class NotEqualBothVar : public CachedConditionFunctioning {
    DoublePtr * compareWhatValue;
    DoublePtr * compareWithValue;
public:
    NotEqualBothVar(DoublePtr* compareWhatValue, DoublePtr* compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue->value);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue->value);
        return *compareWhatValue->value != *compareWithValue->value;
    }
};

class NotEqualImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        DataOperation* compareWhatOp = compareWhat->getDataOperation();
        DataOperation* compareWithOp = compareWith->getDataOperation();

        DoublePtr* compareWhatValue = compareWhat->getVar();
        DoublePtr* compareWithValue = compareWith->getVar();

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

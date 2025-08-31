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
    DoublePtr *compareWhatValue;
    DataOperation *compareWithOp;
public:
    LessThanLeftVar(DoublePtr *compareWhatValue, DataOperation *compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue->value);
        return *compareWhatValue->value < compareWithOp->get();
    }
};

class LessThanRightVar : public CachedConditionFunctioning {
    DataOperation *compareWhatOp;
    DoublePtr *compareWithValue;
public:
    LessThanRightVar(DataOperation *compareWhatOp, DoublePtr *compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() < *compareWithValue->value;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue->value);
        return val;
    }
};

class LessThanBothVar : public CachedConditionFunctioning {
    DoublePtr *compareWhatValue;
    DoublePtr *compareWithValue;
public:
    LessThanBothVar(DoublePtr *compareWhatValue, DoublePtr *compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue->value);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue->value);
        return *compareWhatValue->value < *compareWithValue->value;
    }
};


class LessThanImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {

        DataOperation *compareWhatOp = compareWhat->getDataOperation();
        DataOperation *compareWithOp = compareWith->getDataOperation();

        DoublePtr *compareWhatValue = compareWhat->getVar();
        DoublePtr *compareWithValue = compareWith->getVar();


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

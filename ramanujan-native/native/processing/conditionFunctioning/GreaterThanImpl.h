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
    DoublePtr * compareWhatValue;
    DataOperation* compareWithOp;
public:
    GreaterThanLeftVar(DoublePtr* compareWhatValue, DataOperation* compareWithOp) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithOp = compareWithOp;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue->value);
        return *compareWhatValue->value > compareWithOp->get();
    }
};

class GreaterThanRightVar : public CachedConditionFunctioning {
    DataOperation* compareWhatOp;
    DoublePtr * compareWithValue;
public:
    GreaterThanRightVar(DataOperation* compareWhatOp, DoublePtr* compareWithValue) {
        this->compareWhatOp = compareWhatOp;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        bool val = compareWhatOp->get() > *compareWithValue->value;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue->value);
        return val;
    }
};

class GreaterThanBothVar : public CachedConditionFunctioning {
    DoublePtr * compareWhatValue;
    DoublePtr * compareWithValue;

public:
    GreaterThanBothVar(DoublePtr* compareWhatValue, DoublePtr* compareWithValue) {
        this->compareWhatValue = compareWhatValue;
        this->compareWithValue = compareWithValue;
    }

    bool operate() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWhatValue->value);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(compareWithValue->value);
        return *compareWhatValue->value > *compareWithValue->value;
    }
};

class GreaterThanImpl : public ConditionFunctioning {
public:
    CachedConditionFunctioning *
    getConditionFunctioning(CommandRE *compareWhat, CommandRE *compareWith) override {
        DataOperation* compareWhatOp = compareWhat->getDataOperation();
        DataOperation* compareWithOp = compareWith->getDataOperation();

        DoublePtr* compareWhatValue = compareWhat->getVar();
        DoublePtr* compareWithValue = compareWith->getVar();

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

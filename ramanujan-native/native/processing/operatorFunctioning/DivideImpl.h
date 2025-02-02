//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_DIVIDEIMPL_H
#define NATIVE_DIVIDEIMPL_H

#include "../../ruleEngineObject/DataOperation.h"
#include "CachedOperationFunctioning.h"
#include "OperationFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"

class DivideImplCachedOperationFunctioning : public CachedOperationFunctioning {
public:
    DataOperation* op1;
    DataOperation* op2;

    DivideImplCachedOperationFunctioning(DataOperation* op1, DataOperation* op2) {
        this->op1 = op1;
        this->op2 = op2;
    }

    double get() override {
        return op1->get() / op2->get();
    }
};


class DivideImplLeftVar : public CachedOperationFunctioning {
    double * v1;
    DataOperation* op2;
public:

    DivideImplLeftVar(double * v1, DataOperation* op2) {
        this->v1 = v1;
        this->op2 = op2;
    }

    double get() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v1);
        return *v1 / op2->get();
    }
};

class DivideImplRightVar : public CachedOperationFunctioning {
    DataOperation* op1;
    double * v2;
public:

    DivideImplRightVar(DataOperation* op1, double * v2) {
        this->op1 = op1;
        this->v2 = v2;
    }

    double get() override {
        double val = op1->get() / *v2;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v2);
        return val;
    }
};

class DivideImplBothVar : public CachedOperationFunctioning {
    double * v1;
    double * v2;
public:

    DivideImplBothVar(double * v1, double * v2) {
        this->v1 = v1;
        this->v2 = v2;
    }

    double get() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v1);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v2);
        return *v1 / *v2;
    }
};

class DivideImpl : public OperationFunctioning {
public:
    CachedOperationFunctioning *
    getOperationFunctioning(CommandRE *commandRe1, CommandRE *commandRe2) override {
        DataOperation* op1 = commandRe1->getDataOperation();
        DataOperation* op2 = commandRe2->getDataOperation();

        double *v1 = commandRe1->getVar();
        double * v2 = commandRe2->getVar();

        if (v1 != nullptr && v2 != nullptr) {
            return new DivideImplBothVar(v1, v2);
        }

        if(v1 != nullptr) {
            return new DivideImplLeftVar(v1, op2);
        }

        if(v2 != nullptr) {
            return new DivideImplRightVar(op1, v2);
        }

        return new DivideImplCachedOperationFunctioning(op1, op2);
    }
};
#endif //NATIVE_DIVIDEIMPL_H

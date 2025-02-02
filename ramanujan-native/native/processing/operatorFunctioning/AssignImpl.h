//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_ASSIGNIMPL_H
#define NATIVE_ASSIGNIMPL_H

#include "../../ruleEngineObject/DataOperation.h"
#include "CachedOperationFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"
#include "OperationFunctioning.h"
#include "../DebugPoint.h"

class AssignImplCachedOperationFunctioningBase : public CachedOperationFunctioning {
public:
    virtual void set() = 0;
};

class AssignImplCachedOperationFunctioning : public AssignImplCachedOperationFunctioningBase {
public:
    DataOperation *op1;
    DataOperation *op2;
    std::string *processId;

    AssignImplCachedOperationFunctioning() {}

    AssignImplCachedOperationFunctioning(DataOperation *op1, DataOperation *op2) {
        this->op1 = op1;
        this->op2 = op2;
        this->processId = processId;
    }

    double get() override {
        op1->set(op2->get());
        return 0;
    }

    void set() override {
        DEBUG_PRE();
        DEBUG_ADD_DATA_OP_SET_BEFORE(op1);
        double val = op2->get();
        op1->set(val);
        DEBUG_ADD_DATA_DOUBLE_SET_AFTER(val);
    }
};

//take ispiration from AddImpl and have the impl classes
class AssignImplLeftVar : public AssignImplCachedOperationFunctioningBase {
    double *v1;
    DataOperation *op2;

public:

    AssignImplLeftVar(double *v1, DataOperation *op2) {
        this->v1 = v1;
        this->op2 = op2;
    }

    double get() override {
        *v1 = op2->get();
        return 0;
    }

    void set() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v1);
        double val = op2->get();
        *v1 = val;
        DEBUG_ADD_DATA_DOUBLE_SET_AFTER(val);
    }

};

class AssignImplRightVar : public AssignImplCachedOperationFunctioningBase {
    DataOperation *op1;
    double *v2;

public:

    AssignImplRightVar(DataOperation *op1, double *v2) {
        this->op1 = op1;
        this->v2 = v2;
    }

    double get() override {
        op1->set(*v2);
        return 0;
    }

    void set() override {
        DEBUG_PRE();
        DEBUG_ADD_DATA_OP_SET_BEFORE(op1);
        double val = *v2;
        op1->set(val);
        DEBUG_ADD_DATA_DOUBLE_SET_AFTER(val);
    }
};

class AssignImplBothVar : public AssignImplCachedOperationFunctioningBase {
    double *v1;
    double *v2;

public:

    AssignImplBothVar(double *v1, double *v2) {
        this->v1 = v1;
        this->v2 = v2;
    }

    double get() override {
        *v1 = *v2;
        return 0;
    }

    void set() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v1);
        double val = *v2;
        *v1 = val;
        DEBUG_ADD_DATA_DOUBLE_SET_AFTER(val);
    }
};

class AssignImpl : public OperationFunctioning {
public:
    CachedOperationFunctioning *
    getOperationFunctioning(CommandRE *commandRe1, CommandRE *commandRe2) override {
        DataOperation *op1 = commandRe1->getDataOperation();
        DataOperation *op2 = commandRe2->getDataOperation();

        double *v1 = commandRe1->getVar();
        double *v2 = commandRe2->getVar();


        if (v1 != nullptr && v2 != nullptr) {
            return new AssignImplBothVar(v1, v2);
        } else if (v1 != nullptr) {
            return new AssignImplLeftVar(v1, op2);
        } else if (v2 != nullptr) {
            return new AssignImplRightVar(op1, v2);
        }

        return new AssignImplCachedOperationFunctioning(op1, op2);
    }
};

#endif //NATIVE_ASSIGNIMPL_H

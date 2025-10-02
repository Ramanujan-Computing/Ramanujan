//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_ADDIMPL_H
#define NATIVE_ADDIMPL_H

#include "OperationFunctioning.h"

class AddImplCachedOperationFunctioning : public CachedOperationFunctioning {
public:
    DataOperation* op1;
    DataOperation* op2;

    AddImplCachedOperationFunctioning(DataOperation* op1, DataOperation* op2) {
        this->op1 = op1;
        this->op2 = op2;
    }

    double get() override {
        return op1->get() + op2->get();
    }
};

class AddImplLeftVar : public CachedOperationFunctioning {
    DoublePtr *v1;
    DataOperation *op2;

public:

    AddImplLeftVar(DoublePtr *v1, DataOperation *op2) {
        this->v1 = v1;
        this->op2 = op2;
    }

    double get() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v1->value);
        return v1->value + op2->get();
    }
};

class AddImplRightVar : public CachedOperationFunctioning {
    DataOperation *op1;
    DoublePtr *v2;

public:

        AddImplRightVar(DataOperation *op1, DoublePtr *v2) {
            this->op1 = op1;
            this->v2 = v2;
        }

        double get() override {
#ifndef DEBUG_BUILD
            return op1->get() + v2->value;
#endif
            double val = op1->get() + v2->value;
            DEBUG_PRE();
            DEBUG_ADD_DOUBLE_PTR_BEFORE(v2->value);
            return val;
        }

};

class AddImplBothVar : public CachedOperationFunctioning {
    DoublePtr *v1;
    DoublePtr *v2;

public:

        AddImplBothVar(DoublePtr *v1, DoublePtr *v2) {
            this->v1 = v1;
            this->v2 = v2;
        }

        double get() override {
            DEBUG_PRE();
            DEBUG_ADD_DOUBLE_PTR_BEFORE(v1->value);
            DEBUG_ADD_DOUBLE_PTR_BEFORE(v2->value);
            return v1->value + v2->value;
        }
    };

class AddImpl : public OperationFunctioning {
public:
    CachedOperationFunctioning *
    getOperationFunctioning(CommandRE *commandRe1, CommandRE *commandRe2) override {
        DataOperation* op1 = commandRe1->getDataOperation();
        DataOperation* op2 = commandRe2->getDataOperation();

        DoublePtr *v1 = commandRe1->getVar();
        DoublePtr *v2 = commandRe2->getVar();

        if (v1 != nullptr && v2 != nullptr) {
            return new AddImplBothVar(v1, v2);
        } else if (v1 != nullptr) {
            return new AddImplLeftVar(v1, op2);
        } else if (v2 != nullptr) {
            return new AddImplRightVar(op1, v2);
        }
        return new AddImplCachedOperationFunctioning(op1, op2);
    }
};

#endif //NATIVE_ADDIMPL_H

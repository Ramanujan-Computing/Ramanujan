//
// Created by Pranav on 06/04/24.
//

#ifndef NATIVE_MINUSIMPL_H
#define NATIVE_MINUSIMPL_H

#include "CachedOperationFunctioning.h"
#include "../../ruleEngineObject/CommandRE.h"
#include "OperationFunctioning.h"

class MinusCachedOperationFunctioning : public CachedOperationFunctioning {
public:
    std::unordered_map<std::string, RuleEngineInputUnits *> *map;
    std::string *processId;

    DataOperation *operand1Op;
    DataOperation *operand2Op;

    MinusCachedOperationFunctioning(DataOperation *operandCommandRE1, DataOperation *operandCommandRE2) {

        operand1Op = operandCommandRE1;
        operand2Op = operandCommandRE2;
    }

    double get() override {
        return operand1Op->get() - operand2Op->get();
    }
};


class MinusImplLeftVar : public CachedOperationFunctioning {
    DoublePtr *v1;
    DataOperation *op2;

public:

    MinusImplLeftVar(DoublePtr *v1, DataOperation *op2) {
        this->v1 = v1;
        this->op2 = op2;
    }

    double get() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v1->value);
        return v1->value - op2->get();
    }

};

class MinusImplRightVar : public CachedOperationFunctioning {
    DataOperation *op1;
    DoublePtr *v2;

public:

    MinusImplRightVar(DataOperation *op1, DoublePtr *v2) {
        this->op1 = op1;
        this->v2 = v2;
    }

    double get() override {
        double val = op1->get() - v2->value;
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v2->value);
        return val;
    }
};

class MinusImplBothVar : public CachedOperationFunctioning {
    DoublePtr *v1;
    DoublePtr *v2;

public:

    MinusImplBothVar(DoublePtr *v1, DoublePtr *v2) {
        this->v1 = v1;
        this->v2 = v2;
    }

    double get() override {
        DEBUG_PRE();
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v1->value);
        DEBUG_ADD_DOUBLE_PTR_BEFORE(v2->value);
        return v1->value - v2->value;
    }
};


class MinusImpl : public OperationFunctioning {
public:
    CachedOperationFunctioning *
    getOperationFunctioning(CommandRE *operandCommandRE1, CommandRE *operandCommandRE2) override {
        DataOperation *operand1Op = operandCommandRE1->getDataOperation();
        DataOperation *operand2Op = operandCommandRE2->getDataOperation();

        DoublePtr *v1 = operandCommandRE1->getVar();
        DoublePtr *v2 = operandCommandRE2->getVar();

        if (v1 != nullptr && v2 != nullptr) {
            return new MinusImplBothVar(v1, v2);
        } else if (v1 != nullptr) {
            return new MinusImplLeftVar(v1, operand2Op);
        } else if (v2 != nullptr) {
            return new MinusImplRightVar(operand1Op, v2);
        } else {
            return new MinusCachedOperationFunctioning(operand1Op, operand2Op);
        }
    }
};

#endif //NATIVE_MINUSIMPL_H

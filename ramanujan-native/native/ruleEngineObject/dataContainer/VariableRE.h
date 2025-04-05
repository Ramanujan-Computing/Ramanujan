//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_VARIABLERE_H
#define NATIVE_VARIABLERE_H

#include "Variable.hpp"
#include "../ruleEngineObject/ConstantRE.h"
#include "DataContainerRE.h"
#include "../processing/ProcessingResult.hpp"
#include "array/ArrayValue.h"
#include <stack>
#include <cmath>

class DoublePtr : public RuleEngineInputUnits{
public:
    virtual double* getValPtrPtr() = 0;
};


class VariableRE : public DoublePtr {
    Variable *variable;

    double value;

    bool added = false;

public:
    std::string name, frameCount;
    VariableRE(Variable *variable) {
        this->variable = variable;

        id = variable->id;
        name = variable->name;
        double  val = variable->value;
        if(std::isnan(val)) {
            val = 0;
        }
        value = val;
        frameCount = variable->frameCount;
    }

    void destroy() override {

    }

    void setValue(double value) {
        this->value = value;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {

    }

    void process() override {

    }

    double* getValPtrPtr() override {
        return &value;
    }
};

class ConstantRE : public DoublePtr {
private:
    double value;

public:
    ConstantRE(Constant* constant) {
        this->value =constant->value;
    }

    void destroy() {
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {

    }

    void process() override {
    }

    double *getValPtrPtr() override {
        return &value;
    }
};
#endif //NATIVE_VARIABLERE_H

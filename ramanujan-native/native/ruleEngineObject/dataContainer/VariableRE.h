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

// Forward declaration
class DataContainerValueFunctionCommandRE;

class DoublePtr : public DataContainerValue{
public:
    double* value = nullptr;

    DoublePtr() = default;

    ~DoublePtr() = default;

    DoublePtr(double  val) : value(new double(val)) {}

    void copyDataContainerValue(DataContainerValue* toBeCopied) override
    {
        if(value != nullptr) {
            //delete value;
            //TODO: pranav: check if this is causing memory leak
        }
        *value = *(((DoublePtr*)toBeCopied)->value);
    }

    void copyDataContainerValue(DataContainerValueFunctionCommandRE& toBeCopied) override;

    DataContainerValueType getType() const override {
        return DataContainerValueType::DOUBLE_PTR;
    }

    DataContainerValue* clone() override {
        return new DoublePtr(*value);
    }


};


class VariableRE : public RuleEngineInputUnits, public AbstractDataContainer {
    Variable *variable;

    DoublePtr doublePtr;

    bool added = false;

public:
    std::string name, frameCount;
    VariableRE(Variable *variable) : doublePtr(std::isnan(variable->value) ? 0.0 : variable->value) {
        this->variable = variable;

        id = variable->id;
        name = variable->name;
        valPtr = &doublePtr;
        frameCount = variable->frameCount;
    }

    void destroy() override {
    }

    double* getValPtrPtr() {
        return doublePtr.value;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {

    }

    void process() override {

    }
};

class ConstantRE : public RuleEngineInputUnits, public AbstractDataContainer {
private:
    DoublePtr doublePtr;

public:
    ConstantRE(Constant* constant) : doublePtr(constant->value){
        id = constant->id;
        valPtr = &doublePtr;
    }

    void destroy() {
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {

    }

    void process() override {
    }
};
#endif //NATIVE_VARIABLERE_H

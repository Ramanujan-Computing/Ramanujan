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
#include "DataContainerValueFunctionCommandRE.h"

class DoublePtr : public DataContainerValue{
public:
    double value = 0.0;

    DoublePtr() = default;

    ~DoublePtr() = default;

    DoublePtr(double  val) : value(val) {}

    void copyDataContainerValue(DataContainerValue* toBeCopied) override
    {
        value = ((DoublePtr*)toBeCopied)->value;
    }

    // PERFORMANCE CRITICAL: Inlined to eliminate function call overhead (~11% of execution time)
    inline void copyDataContainerValue(DataContainerValueFunctionCommandRE& toBeCopied) override;

    DataContainerValueType getType() const override {
        return DataContainerValueType::DOUBLE_PTR;
    }

    DataContainerValue* clone() override {
        return new DoublePtr(value);
    }

    // PERFORMANCE CRITICAL: Inlined to eliminate function call overhead (~11% of execution time)
    inline void setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) override;

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
        return &doublePtr.value;
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

// ==================== INLINE METHOD IMPLEMENTATIONS ====================
// These methods are performance-critical (showing up as ~11% in flamegraph)
// Inlining eliminates virtual function call overhead while maintaining polymorphism

inline void DoublePtr::copyDataContainerValue(DataContainerValueFunctionCommandRE& toBeCopied) {
    value = toBeCopied.value;
}

inline void DoublePtr::setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) {
    toBeSet.value = value;
}

#endif //NATIVE_VARIABLERE_H

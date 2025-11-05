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

    // PERFORMANCE CRITICAL: Inlined to eliminate function call overhead (~11% of execution time)
    inline void copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeCopied) override;

    // PERFORMANCE CRITICAL: Inlined to eliminate function call overhead (~11% of execution time)
    inline void setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) override;
    
    // Combined method to save value and copy from source in one call - eliminates extra pointer hop
    inline void saveValueAndCopyFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValue* source) override;
    
    // Combined method to save current value and restore from saved value in one call - eliminates extra pointer hop
    inline void saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValueFunctionCommandRE& restoreFrom) override;

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

class MethodAgnosticVariableInternal : public DataContainerValue {
    double doubleValue = 0.0;
    double * arrayValue = nullptr;
    bool isArray = false;
    bool isDataTypeKnown = false;

    void copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeCopied) override
    {
        if (isDataTypeKnown)
        {
            if (isArray)
            {
                arrayValue = toBeCopied.arrayValuePtr;
            }
            else
            {
                doubleValue = toBeCopied.value;
            }
            return;
        }
        if(toBeCopied.arrayValuePtr)
        {
            isArray = true;
            arrayValue = toBeCopied.arrayValuePtr;
            isDataTypeKnown = true;
        }
        else
        {
            isArray = false;
            doubleValue = toBeCopied.value;
            isDataTypeKnown = true;
        }
    }

    void setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) override
    {

    }

    void saveValueAndCopyFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValue* source) override
    {
        if(isDataTypeKnown)
        {
            if (isArray)
            {
                savedValue.arrayValuePtr = arrayValue;
                arrayValue = ((MethodAgnosticVariableInternal*)source)->arrayValue;
            }
            else
            {
                savedValue.value = doubleValue;
                doubleValue = ((MethodAgnosticVariableInternal*)source)->doubleValue;
            }
        } else {
            // since dataType is not known, func called first time then. No need to save previous value.
//            savedValue.arrayValuePtr = arrayValue;
//            savedValue.value = doubleValue;

            DoublePtr* sourceDoublePtr = dynamic_cast<DoublePtr*>(source);
            if(sourceDoublePtr)
            {
                isArray = false;
                doubleValue = sourceDoublePtr->value;
                isDataTypeKnown = true;
            } else {
                MethodAgnosticVariableInternal* methodAgnosticVariableInternal = dynamic_cast<MethodAgnosticVariableInternal*>(source);
                if (methodAgnosticVariableInternal)
                {
                    isArray = true;
                    arrayValue = methodAgnosticVariableInternal->arrayValue;
                    isDataTypeKnown = true;
                }
                else{
                    copyArrayValueFromDataContainerValue(source);
                }
            }
        }
    }

    void copyArrayValueFromDataContainerValue(DataContainerValue* source);

    void saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValueFunctionCommandRE& restoreFrom) override
    {
        if(isDataTypeKnown)
        {
            if (isArray)
            {
                savedValue.arrayValuePtr = arrayValue;
                arrayValue = restoreFrom.arrayValuePtr;
            }
            else
            {
                savedValue.value = doubleValue;
                doubleValue = restoreFrom.value;
            }
        }
    }
};

class MethodAgnosticVariableRE : public RuleEngineInputUnits, public AbstractDataContainer {
    MethodAgnosticVariable *variable;
    MethodAgnosticVariableInternal methodAgnosticVariableInternal;
public:
    MethodAgnosticVariableRE(MethodAgnosticVariable *variable) {
        this->variable = variable;

        id = variable->id;
        valPtr = &methodAgnosticVariableInternal;
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

inline void DoublePtr::copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeCopied) {
    value = toBeCopied.value;
}

inline void DoublePtr::setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) {
    toBeSet.value = value;
}

inline void DoublePtr::saveValueAndCopyFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValue* source) {
    savedValue.value = value;
    value = ((DoublePtr*)source)->value;
}

inline void DoublePtr::saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValueFunctionCommandRE& restoreFrom) {
    savedValue.value = value;
    value = restoreFrom.value;
}

#endif //NATIVE_VARIABLERE_H

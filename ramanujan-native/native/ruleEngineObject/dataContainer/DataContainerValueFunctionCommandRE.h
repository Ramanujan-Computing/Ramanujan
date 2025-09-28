//
// Created by Pranav on 28/09/25.
//

#ifndef NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDRE_H
#define NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDRE_H

#include "VariableRE.h"
#include "ArrayRE.h"

class DataContainerValueFunctionCommandRE {
public:
    // Optimized default constructor - explicitly initialize virtual base class once
    DataContainerValueFunctionCommandRE() {
        // Fast path - virtual inheritance ensures DataContainerValue constructor called only once
        value = nullptr;
        arrayValue = nullptr;
    }

    double *value;
    ArrayValue *arrayValue;

    ~DataContainerValueFunctionCommandRE() = default;
    
    // Copy constructor optimized for performance
    DataContainerValueFunctionCommandRE(const DataContainerValueFunctionCommandRE& other) {
        // Shallow copy of pointers - no expensive deep copies
        value = other.value;
        arrayValue = other.arrayValue; 
    }
    
    // Move constructor for performance optimization
    DataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE&& other) noexcept
    {
        value = other.value;
        arrayValue = other.arrayValue;
        other.value = nullptr;
        other.arrayValue = nullptr;
    }
    
    // Fast assignment operator
    DataContainerValueFunctionCommandRE& operator=(const DataContainerValueFunctionCommandRE& other) {
        if (this != &other) {
            value = other.value;
            arrayValue = other.arrayValue;
        }
        return *this;
    }
    
    // Move assignment operator for performance
    DataContainerValueFunctionCommandRE& operator=(DataContainerValueFunctionCommandRE&& other) noexcept {
        if (this != &other) {
            value = other.value;
            arrayValue = other.arrayValue;
            other.value = nullptr;
            other.arrayValue = nullptr;
        }
        return *this;
    }
    void copyDataContainerValue(DataContainerValue* toBeCopied)
    {
        switch(toBeCopied->getType()) {
            case DataContainerValueType::DOUBLE_PTR: {
                auto doublePtr = (DoublePtr*)(toBeCopied);
                value = new double(*doublePtr->value);
                break;
            }
            case DataContainerValueType::ARRAY_DATA_CONTAINER_VALUE: {
                auto arrayDataContainerValue = (ArrayDataContainerValue*)(toBeCopied);
                arrayValue = new ArrayValue(arrayDataContainerValue->arrayValue, true);
                break;
            }
        }
    }

    void copyDataContainerValue(DataContainerValueFunctionCommandRE& toBeCopied)
    {
        // Copy both the variable and array components from the source
        value = toBeCopied.value;
        arrayValue = toBeCopied.arrayValue;
    }
};

#endif //NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDRE_H

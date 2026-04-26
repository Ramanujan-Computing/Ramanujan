//
// Created by pranav on 26/3/24.
//

#ifndef NATIVE_VARIABLERE_CPP
#define NATIVE_VARIABLERE_CPP

#include "VariableRE.h"
#include "DataContainerValueFunctionCommandRE.h"
#include "array/ArrayValue.h"

class MethodAgnosticVariableInternal : public ArrayDataContainerValue {
    bool isArray = false;
    bool isDataTypeKnown = false;

    void copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE* toBeCopied) override
    {
        if (isDataTypeKnown)
        {
            if (isArray)
            {
                arrayValue->val = toBeCopied->arrayValuePtr;
            }
            else
            {
                value = toBeCopied->value;
            }
            return;
        }
        if(toBeCopied->arrayValuePtr)
        {
            isArray = true;
            arrayValue->val = toBeCopied->arrayValuePtr;
            isDataTypeKnown = true;
        }
        else
        {
            isArray = false;
            value = toBeCopied->value;
            isDataTypeKnown = true;
        }
    }

    void setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE* toBeSet) override
    {

    }

    void saveValueAndCopyFrom(DataContainerValueFunctionCommandRE* savedValue, DataContainerValue* source) override
    {
        if(isDataTypeKnown)
        {
            if (isArray)
            {
                savedValue->arrayValuePtr = arrayValue->val;
                arrayValue = ((ArrayDataContainerValue*)source)->arrayValue;
            }
            else
            {
                savedValue->value = value;
                value = ((DoublePtr*)source)->value;
            }
        } else {
            // since dataType is not known, func called first time then. No need to save previous value.
//            savedValue->arrayValuePtr = arrayValue;
//            savedValue->value = doubleValue;

            DoublePtr* sourceDoublePtr = dynamic_cast<DoublePtr*>(source);
            if(sourceDoublePtr)
            {
                isArray = false;
                value = sourceDoublePtr->value;
                isDataTypeKnown = true;
            } else {
                MethodAgnosticVariableInternal* methodAgnosticVariableInternal = dynamic_cast<MethodAgnosticVariableInternal*>(source);
                if (methodAgnosticVariableInternal)
                {
                    //isArray = true;
                    arrayValue = methodAgnosticVariableInternal->arrayValue;
                    if (arrayValue)
                    {
                        isArray = true;
                        arrayValue = new ArrayValue(arrayValue, true);
                    }
                    value = methodAgnosticVariableInternal->value;
                    //isDataTypeKnown = true;
                    return;
                }
                copyArrayValueFromDataContainerValue(source);
            }
        }
    }

    void copyArrayValueFromDataContainerValue(DataContainerValue* source);

    void saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValueFunctionCommandRE* restoreFrom) override
    {
        if(isDataTypeKnown)
        {
            if (isArray)
            {
                savedValue.arrayValuePtr = arrayValue->val;
                arrayValue->val = restoreFrom->arrayValuePtr;
            }
            else
            {
                savedValue.value = value;
                value = restoreFrom->value;
            }
        }
    }

    float* arrayPlaceHolder = nullptr;
    
    void saveRestoreAndPropagate(DataContainerValueFunctionCommandRE* restoreFrom, DataContainerValue* propagateTo) override
    {
        if(isDataTypeKnown)
        {
            if (isArray)
            {
                // Save current value (final computed result)
                arrayPlaceHolder = arrayValue->val;
                // Restore from previous saved value
                arrayValue->val = restoreFrom->arrayValuePtr;
                // Propagate final value to calling context
                ((ArrayDataContainerValue*)propagateTo)->arrayValue->val = arrayPlaceHolder;
            }
            else
            {
                // Save current value (final computed result)
                double finalValue = value;
                // Restore from previous saved value
                value = restoreFrom->value;
                // Propagate final value to calling context
                propagateTo->value = finalValue;
            }
        }
    }
};

void MethodAgnosticVariableInternal::copyArrayValueFromDataContainerValue(DataContainerValue *source) {
    ArrayDataContainerValue* arrayDataContainerValue = dynamic_cast<ArrayDataContainerValue*>(source);
    if(arrayDataContainerValue)
    {
        isArray = true;
        arrayValue = new ArrayValue(arrayDataContainerValue->arrayValue, true);
        isDataTypeKnown = true;
    }
}

MethodAgnosticVariableRE::MethodAgnosticVariableRE(MethodAgnosticVariable *variable) {
    methodAgnosticVariableInternal = new MethodAgnosticVariableInternal();
    this->variable = variable;

    id = variable->id;
    valPtr = methodAgnosticVariableInternal;
}



#endif


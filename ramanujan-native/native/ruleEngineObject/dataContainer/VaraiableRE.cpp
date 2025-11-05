//
// Created by pranav on 26/3/24.
//

#ifndef NATIVE_VARIABLERE_CPP
#define NATIVE_VARIABLERE_CPP

#include "VariableRE.h"
#include "DataContainerValueFunctionCommandRE.h"
#include "array/ArrayValue.h"

void MethodAgnosticVariableInternal::copyArrayValueFromDataContainerValue(DataContainerValue *source) {
    ArrayDataContainerValue* arrayDataContainerValue = dynamic_cast<ArrayDataContainerValue*>(source);
    if(arrayDataContainerValue)
    {
        isArray = true;
        arrayValue = arrayDataContainerValue->arrayValue->val;
        isDataTypeKnown = true;
    }
}

#endif


// RedefineArrayCommandRE.cpp
#include "RedefineArrayCommandRE.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/array/ArrayValue.h"
#include <iostream>
#include <vector>
#include <string>
#include <json/json.h>

void RedefineArrayCommandRE::process() {
    if (!arrayValuePtr) {
        return;
    }
    // 1. Compute new dimensions using the class field dims
    for (int i = 0; i < dimsCount; ++i) {
        if (isVariableDimension[i] && dimensionVariableREs[i]) {
            dims[i] = static_cast<int>(*dimensionVariableREs[i]->getValPtrPtr());
        } else if (!isVariableDimension[i]) {
            dims[i] = staticDimensions[i];
        } else {
            dims[i] = 1; // fallback
        }
    }

    // 2. Create a new Array object with new dimensions
    Array* newArray = new Array();
    newArray->id = arrayId;
    newArray->dimension.clear();
    for (int i = 0; i < dimsCount; ++i) {
        newArray->dimension.push_back(dims[i]);
    }
    newArray->dimensionSize = dimsCount;
    // Optionally, copy dataType, name, etc. if needed

    // 3. Create a new ArrayValue with the new Array
    ArrayValue* oldArrayValue = *arrayValuePtr;
    ArrayValue* newArrayValue = new ArrayValue(newArray, arrayId);

    // 4. Update the pointer and delete the old ArrayValue
    *arrayValuePtr = newArrayValue;
    if (oldArrayValue) {
        oldArrayValue->destroy();
        delete oldArrayValue;
    }
}

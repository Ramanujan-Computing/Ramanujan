//
// Created by Pranav on 09/06/24.
//

#include "ArrayValue.h"
#include "../DataContainerValueFunctionCommandRE.h"


ArrayValue::ArrayValue(Array* array , std::string originalArrayId) {
    this->array = array;

    dimensionSize = array->dimensionSize;
    dimensions = new int[dimensionSize];
    if(dimensionSize == 0) {
        return;
    }

    if(dimensionSize > 0) {
        sizeAtIndex = new int[dimensionSize];
    }
    int i = 0;
    for (int dim : array->dimension) {
        sizeAtIndex[i] = -1;
        dimensions[i++] = dim;
    }

    totalSize = getTotalSize(dimensions, 0, dimensionSize);
    val = new double[totalSize]();

    // Fast path: load from binary float32 file directly into val[]
    if (!array->binaryFile.empty()) {
        std::ifstream file(array->binaryFile, std::ios::binary | std::ios::ate);
        if (file.is_open()) {
            size_t fileSize = file.tellg();
            file.seekg(0, std::ios::beg);
            size_t numFloats = fileSize / sizeof(float);
            int count = (int)numFloats < totalSize ? (int)numFloats : totalSize;

            // Read float32 values and convert to double
            std::vector<float> floatBuf(count);
            file.read(reinterpret_cast<char*>(floatBuf.data()), count * sizeof(float));
            file.close();

            for (int idx = 0; idx < count; idx++) {
                val[idx] = (double)floatBuf[idx];
            }
            std::cout << "[ArrayValue] Loaded " << count << " values from binary: " << array->binaryFile << std::endl;
        } else {
            std::cerr << "[ArrayValue] Failed to open binary file: " << array->binaryFile << std::endl;
        }
    } else {
        // Slow path: parse string-keyed map
        for(auto & it : array->values) {
            std::string key = it.first;
            double value = it.second;
            //TODO: check if the size is faring correct.
            add(getIndexFromStr(key, dimensionSize), value);
        }
    }
}

void ArrayValue::add(int* index, double value) {
    int indexInt = translateIndex(index);
    val[indexInt] = (value);
}

void ArrayDataContainerValue::copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE* toBeCopied) {
    //delete arrayValue;
    //TODO: pranav: check if this is causing memory leak
    arrayValue->val = toBeCopied->arrayValuePtr;
}

void ArrayDataContainerValue::setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE* toBeSet) {
    // Clean up current array value if present
    toBeSet->arrayValuePtr = arrayValue->val;
}

void ArrayDataContainerValue::saveValueAndCopyFrom(DataContainerValueFunctionCommandRE* savedValue, DataContainerValue* source) {
    // Save current value
    savedValue->arrayValuePtr = arrayValue->val;
    oldValue = arrayValue;
    // Copy from source
    arrayValue = new ArrayValue(((ArrayDataContainerValue*) source)->arrayValue, true);
    delete oldValue;
}

void ArrayDataContainerValue::saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValueFunctionCommandRE* restoreFrom) {
    // Save current value
    savedValue.arrayValuePtr = arrayValue->val;
    // Restore from saved value

    arrayValue->val = restoreFrom->arrayValuePtr;
}

void ArrayDataContainerValue::saveRestoreAndPropagate(DataContainerValueFunctionCommandRE* restoreFrom, DataContainerValue* propagateTo) {
    // Save current value (final computed result)
    placeholder = arrayValue->val;
    // Restore from previous saved value
    arrayValue->val = restoreFrom->arrayValuePtr;
    // Propagate final value to calling context
    ((ArrayDataContainerValue*)propagateTo)->arrayValue->val = placeholder;
}

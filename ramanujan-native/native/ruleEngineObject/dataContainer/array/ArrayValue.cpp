//
// Created by Pranav on 09/06/24.
//

#include "ArrayValue.h"

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
    for(auto & it : array->values) {
        std::string key = it.first;
        double value = it.second;
        //TODO: check if the size is faring correct.
        add(getIndexFromStr(key, dimensionSize), value);
    }
}

void ArrayValue::add(int* index, double value) {
    int indexInt = translateIndex(index);
    val[indexInt] = (value);
}

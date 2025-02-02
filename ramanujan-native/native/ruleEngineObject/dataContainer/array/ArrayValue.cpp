//
// Created by Pranav on 09/06/24.
//

#include "ArrayValue.h"

ArrayValue::ArrayValue(Array* array , std::string originalArrayId) {
    this->array = array;

    dimensionSize = array->dimension.size();
    dimensions = new int[dimensionSize];
    if(dimensionSize == 0) {
        return;
    }

    if(array->dimension.size() > 0) {
        sizeAtIndex = new int[array->dimension.size()];
    }
    int i = 0;
    for (int dim : array->dimension) {
        sizeAtIndex[i] = -1;
        dimensions[i++] = dim;
    }

    totalSize = getTotalSize(dimensions, 0, array->dimension.size());
    val = new double[totalSize]();
    for(std::unordered_map<std::string, double>::iterator it = array->values.begin(); it != array->values.end(); ++it) {
        std::string key = it->first;
        double value = it->second;
        //TODO: check if the size is faring correct.
        add(getIndexFromStr(key, array->dimension.size()), value);
    }
}

void ArrayValue::add(int* index, double value) {
    int indexInt = translateIndex(index);
    val[indexInt] = (value);
}

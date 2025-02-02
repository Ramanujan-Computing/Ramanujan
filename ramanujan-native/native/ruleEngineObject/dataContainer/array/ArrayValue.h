//
// Created by Pranav on 07/04/24.
//

#ifndef NATIVE_ARRAYVALUE_H
#define NATIVE_ARRAYVALUE_H

#include <string>
#include <unordered_set>
#include <sstream>
#include "../../../input/Array.hpp"
#include "ArrayValDataContainer.h"
#include "../DataContainerValue.h"
#include "../AbstractDataContainer.h"



class AbstractDataContainer;

class ArrayValue : public DataContainerValue {
private:
    Array* array = nullptr;

    int* dimensions = nullptr;

    int dimensionSize = 0;


public:
    double* val = nullptr;
    int totalSize = 0;
    int* sizeAtIndex;
    ArrayValue(Array* array , std::string originalArrayId);

    ArrayValue(ArrayValue* toBeCopied) {
        this->array = toBeCopied->array;
        this->dimensionSize = toBeCopied->dimensionSize;
        this->dimensions = toBeCopied->dimensions;
        this->sizeAtIndex = toBeCopied->sizeAtIndex;
        this->val = new double[toBeCopied->totalSize]();
        this->totalSize = toBeCopied->totalSize;

    }

    void destroy() {
        if(dimensions != nullptr)
            delete[] dimensions;
        if(val != nullptr)
            delete[] val;
    }

    void add(int* index, double value);

    int translateIndex(int* index) {
        int indexInt = 0;
        for(int i = 0; i < dimensionSize - 1; i++) {
            indexInt += sizeAtIndex[i] * index[i];
        }
        indexInt += index[dimensionSize - 1];
        return indexInt;
    }

    std::string to_string(int index) {
        /*
         * translateIndex translates array of index to single index. In this method we want to give back the array of index
         * in the format of index1_index2_.._indexN.
         */

        int indexArray[dimensionSize];
        int indexInt = index;
        for(int i = dimensionSize - 1; i >= 0; i--) {
            indexArray[i] = indexInt % dimensions[i];
            indexInt = indexInt / dimensions[i];
        }
        std::string result = "";
        for(int i = 0; i < dimensionSize - 1; i++) {
            result += std::to_string(indexArray[i]) + "_";
        }
        result += std::to_string(indexArray[dimensionSize - 1]);
        return result;
    }

    int* getIndexFromStr(std::string key, int size) {
        std::string indexDim;
        std::stringstream ss(key);

        int* indexArray = new int[size];

        int i = 0;
        while (getline(ss, indexDim, '_')) {
            indexArray[i++] = fastParseInt(indexDim);
        }

        return indexArray;
    }

    int fastParseInt(const std::string& s) {
        int result = 0;
        for (size_t i = 0; i < s.length(); i++) {
            result = result * 10 + (s[i] - '0');
        }
        return result;
    }

    int getTotalSize(int* dimensions, int index, int size) {
        if(index > 0 && sizeAtIndex[index - 1] != -1) {
            return sizeAtIndex[index - 1];
        }
        if(index == size) {
            return 1;
        }
        int result = dimensions[index] * getTotalSize(dimensions, index + 1, size);
        if(index > 0) {
            sizeAtIndex[index - 1] = result;
        }
        return result;
    }
};


#endif //NATIVE_ARRAYVALUE_H

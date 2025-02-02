//
// Created by Pranav on 07/04/24.
//

#ifndef NATIVE_ARRAYVALDATACONTAINER_H
#define NATIVE_ARRAYVALDATACONTAINER_H

#include "../DataContainerRE.h"
#include "../../DataOperation.h"
#include "../processing/ProcessingResult.hpp"
#include "../../../input/Array.hpp"

class ArrayValueDataContainer : DataOperation {
private:
    double value = 0;
    int *indexArray;
    int dimensionSize = 0;

    bool added = false;


public:
    Array *array;
    ArrayValueDataContainer(double value, int *indexArray) {
        this->value = value;
        this->indexArray = indexArray;
    }

public:

    void setIndexArray(int *indexArray, int dimensionSize, Array *array) {
        this->indexArray = indexArray;
        this->dimensionSize = dimensionSize;
        this->array = array;
    }

    void set(double value) override {
        this->value = value;
        if(!added) {
            added = true;
            ProcessingResult::addArrayValDataContainer(this);
        }

    }

    double get() override {
        return this->value;
    }

    std::string getReadableIndex() {
        std::string index = "";
        for(int i = 0; i < dimensionSize; i++) {
            index += std::to_string(indexArray[i]);
            if(i != dimensionSize - 1) {
                index += "_";
            }
        }
        return index;
    }
};
#endif //NATIVE_ARRAYVALDATACONTAINER_H

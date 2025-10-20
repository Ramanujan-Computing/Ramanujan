//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_ARRAYRE_H
#define NATIVE_ARRAYRE_H

#include "DataContainerRE.h"
#include "array/ArrayValue.h"
#include "iostream"

#include "stack"

class ArrayRE: public RuleEngineInputUnits, public AbstractDataContainer {
private:
    Array* array;

    std::string dataType;

public:
    ArrayDataContainerValue arrayValue;
    std::string name, frameCount;

    ArrayRE(Array *array) : arrayValue(new ArrayValue(array, array->id)){
        this->array = array;

        this->id = array->id;
        this->name = array->name;;
        this->valPtr = &arrayValue;
        this->frameCount = array->frameCount;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {

    }

    void process() override {

    }

    void destroy() {
    }
};
#endif //NATIVE_ARRAYRE_H

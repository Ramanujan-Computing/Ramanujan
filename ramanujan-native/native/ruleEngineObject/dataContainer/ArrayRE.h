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
    ArrayValue* arrayValue;
    std::string name, frameCount;

    ArrayRE(Array *array) {
        this->array = array;

        this->id = array->id;
        this->name = array->name;;
        this->arrayValue = new ArrayValue(array, this->id);
        this->valPtr = reinterpret_cast<DataContainerValue **>(&arrayValue);
        this->frameCount = array->frameCount;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {

    }

    void process() override {

    }

    void destroy() {
        if(arrayValue != nullptr)
        delete arrayValue;
    }

    std::string getId() override {
        return std::string();
    }

    virtual DataContainerValue* getVal() {
        return arrayValue;
    }

    ArrayValue** getValPtr() {
        return &arrayValue;
    }

    virtual void setVal(DataContainerValue *val) {
        arrayValue = (ArrayValue*)(val);
    }
};
#endif //NATIVE_ARRAYRE_H

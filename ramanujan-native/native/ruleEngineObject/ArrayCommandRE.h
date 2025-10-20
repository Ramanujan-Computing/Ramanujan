//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_ARRAYCOMMANDRE_H
#define NATIVE_ARRAYCOMMANDRE_H

#include <string>
#include <list>
#include "dataContainer/DataContainerRE.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
#include <cmath>
#include "DebugPoint.h"


class samplePair {
public:
    double * ptr;
    int index;
};


class ArrayCommandRE : public DataOperation {
private:
    ArrayDataContainerValue* arrayDataContainerValue;
    const int indexSize;
    static const int MAX_DIMENSIONS = 32;// inspired from numpy.
    DoublePtr* valPtrArr[MAX_DIMENSIONS];

    int dimensionSize;
    int dimensionIndex[MAX_DIMENSIONS];

    double translatedIndex = 0;
    int counter = 0;
    ArrayValue * arrayVal;
    int* sizeAtIndex;

    double * getArrayValueDataContainer() {
        translatedIndex = 0;
        arrayVal = arrayDataContainerValue->arrayValue;
        sizeAtIndex = arrayVal->sizeAtIndex;
        //indexes can be only variables.
        for (counter = 0; counter < (dimensionSize - 1); counter++) {

            //int indexVal = valPtrArr[i]->value;//(int) dataContainers[i]->get();
            translatedIndex += sizeAtIndex[counter] * valPtrArr[counter]->value;
        }
        translatedIndex += valPtrArr[dimensionSize - 1]->value;//(int) dataContainers[dimensionSize - 1]->get();
        return arrayVal->val + (int)translatedIndex;
    }

public:
    ArrayCommandRE(ArrayRE *arrayRe, std::vector<std::string*> *index, std::unordered_map<std::string, RuleEngineInputUnits *> *pMap):
    indexSize(index->size()) {
        arrayDataContainerValue = (ArrayDataContainerValue*) arrayRe->getVal();
        //valPtrArr = new DoublePtr *[indexSize];

        dimensionSize = 0;
        for (auto i : *index) {
            auto iterator= pMap->find(*i);
            if(iterator == pMap->end()) {
                throw "Variable not found";
            }
            VariableRE* var = dynamic_cast<VariableRE*>(iterator->second);
//            dataContainers[dimensionSize] = dataContainerRe;
            if(var != nullptr) {
                valPtrArr[dimensionSize] = (DoublePtr*)var->getVal();
            } else {
                ConstantRE* constant = dynamic_cast<ConstantRE*>(iterator->second);
                valPtrArr[dimensionSize] = (DoublePtr*)constant->getVal();
            }
            dimensionSize++;
        }

        //dimensionIndex = new int[dimensionSize];
    }

    ~ArrayCommandRE() {
        if(valPtrArr) {
            for (int i = 0; i < dimensionSize; i++) {
                delete valPtrArr[i];
                valPtrArr[i] = nullptr;
            }
        }
        if(arrayDataContainerValue) {
            delete arrayDataContainerValue;
            arrayDataContainerValue = nullptr;
        }
    }

    void set(double value) override {
       double *ptr = getArrayValueDataContainer();
        *ptr = value;
    }

    double get() override {
#ifdef DEBUG_BUILD
        std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
        debugPoint->canAdd = false;
        double val = *getArrayValueDataContainer();
        debugPoint->canAdd = true;
        debugPoint->addBeforeVal(val);
        return val;
#endif
        return *getArrayValueDataContainer();
    }

};
#endif //NATIVE_ARRAYCOMMANDRE_H

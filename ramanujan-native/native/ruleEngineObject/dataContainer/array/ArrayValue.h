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

// Forward declarations
class AbstractDataContainer;
class DataContainerValueFunctionCommandRE;


class ArrayValue {
private:
    Array* array = nullptr;

    int* dimensions = nullptr;

    int dimensionSize = 0;


public:
    double* val = nullptr;
    int totalSize = 0;
    int* sizeAtIndex = nullptr;
    
    // Optimized default constructor - no allocations
    ArrayValue() : array(nullptr), dimensions(nullptr), dimensionSize(0), 
                   val(nullptr), totalSize(0), sizeAtIndex(nullptr) {
        // Fast initialization - no memory allocations or expensive operations
    }

    ArrayValue(const ArrayValue& other)
    {
        this->array = other.array;
        this->dimensionSize = other.dimensionSize;
        this->dimensions = other.dimensions;
        this->sizeAtIndex = other.sizeAtIndex;
        this->val = other.val;
        this->totalSize = other.totalSize;
    }
    
    // Move constructor for better performance
    ArrayValue(ArrayValue&& other) noexcept
        : array(other.array), dimensions(other.dimensions), dimensionSize(other.dimensionSize),
          val(other.val), totalSize(other.totalSize), sizeAtIndex(other.sizeAtIndex) {
        // Reset moved-from object to prevent double deletion
        other.array = nullptr;
        other.dimensions = nullptr;
        other.dimensionSize = 0;
        other.val = nullptr;
        other.totalSize = 0;
        other.sizeAtIndex = nullptr;
    }
    
    // Move assignment operator
    ArrayValue& operator=(ArrayValue&& other) noexcept {
        if (this != &other) {
            // Clean up current resources if needed
            if (dimensions != nullptr && dimensions != other.dimensions) {
                delete[] dimensions;
            }
            if (val != nullptr && val != other.val) {
                delete[] val;
            }
            if (sizeAtIndex != nullptr && sizeAtIndex != other.sizeAtIndex) {
                delete[] sizeAtIndex;
            }
            
            // Transfer ownership
            array = other.array;
            dimensions = other.dimensions;
            dimensionSize = other.dimensionSize;
            val = other.val;
            totalSize = other.totalSize;
            sizeAtIndex = other.sizeAtIndex;
            
            // Reset moved-from object
            other.array = nullptr;
            other.dimensions = nullptr;
            other.dimensionSize = 0;
            other.val = nullptr;
            other.totalSize = 0;
            other.sizeAtIndex = nullptr;
        }
        return *this;
    }
    
    // Optimized copy assignment operator
    ArrayValue& operator=(const ArrayValue& other) {
        if (this != &other) {
            array = other.array;
            dimensionSize = other.dimensionSize;
            dimensions = other.dimensions;
            sizeAtIndex = other.sizeAtIndex;
            val = other.val;
            totalSize = other.totalSize;
        }
        return *this;
    }

    ArrayValue(Array* array , std::string originalArrayId);

    ArrayValue(ArrayValue& toBeCopied, bool shallowCopy = false)
    {
        this->array = toBeCopied.array;
        this->dimensionSize = toBeCopied.dimensionSize;
        this->dimensions = toBeCopied.dimensions;
        this->sizeAtIndex = toBeCopied.sizeAtIndex;
        if (!shallowCopy)
            this->val = new double[toBeCopied.totalSize]();
        else
            this->val = toBeCopied.val;
        this->totalSize = toBeCopied.totalSize;
    }

    ArrayValue(ArrayValue* toBeCopied, bool shallowCopy = false) {
        this->array = toBeCopied->array;
        this->dimensionSize = toBeCopied->dimensionSize;
        this->dimensions = toBeCopied->dimensions;
        this->sizeAtIndex = toBeCopied->sizeAtIndex;
        if (!shallowCopy)
            this->val = new double[toBeCopied->totalSize]();
        else
            this->val = toBeCopied->val;
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

class ArrayDataContainerValue : public DataContainerValue
{
public:
    ArrayValue * arrayValue = nullptr;
    bool isClone = false;

    ArrayDataContainerValue() = default;
    ~ArrayDataContainerValue() = default;

    ArrayDataContainerValue(ArrayValue* arrayValueIn, bool isClone = false)
    {
        arrayValue = arrayValueIn;
        this->isClone = isClone;
    }

    void setArrayValue(ArrayValue* arrayValueIn) {
        if(arrayValue)
            delete arrayValue;
        arrayValue = arrayValueIn;
    }

    void copyDataContainerValue(DataContainerValue* toBeCopied) override
    {
        //delete arrayValue;
        //TODO: pranav: check if this is causing memory leak
        arrayValue = new ArrayValue(((ArrayDataContainerValue*) toBeCopied)->arrayValue, true);
    }

    void copyDataContainerValue(DataContainerValueFunctionCommandRE& toBeCopied) override;

    DataContainerValueType getType() const override {
        return DataContainerValueType::ARRAY_DATA_CONTAINER_VALUE;
    }

    DataContainerValue* clone() override {
        return new ArrayDataContainerValue(new ArrayValue(arrayValue, true));
    }

    // Ultra-fast direct array value setting - eliminates switch statement overhead
    void setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) override;

//    ~ArrayDataContainerValue() override{
//        //if(!isClone && arrayValue)
//            //delete arrayValue;
//    }
};


#endif //NATIVE_ARRAYVALUE_H

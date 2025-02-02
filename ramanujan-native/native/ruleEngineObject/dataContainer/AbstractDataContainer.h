#ifndef ABSTRACTDATACONTAINER_H
#define ABSTRACTDATACONTAINER_H



#include "array/ArrayValue.h"
#include "DataContainerValue.h"
#include <string>

class ArrayValue;

class AbstractDataContainer {
public:
    DataContainerValue** valPtr;

    virtual std::string getId() = 0;

    virtual DataContainerValue* getVal() = 0;
    virtual void setVal(DataContainerValue* val) = 0;
};

#endif // ABSTRACTDATACONTAINER_H
#ifndef ABSTRACTDATACONTAINER_H
#define ABSTRACTDATACONTAINER_H



#include "array/ArrayValue.h"
#include "DataContainerValue.h"
#include <string>

class ArrayValue;

class AbstractDataContainer {
public:
    DataContainerValue* valPtr;

    /*
     * To be transferred in function stack calls. For example,
     * def func(a,b){}
     *
     * function call:
     * func(x,y);
     *
     * Here, a,b,x,y are all of type AbstractDataContainer.
     * When func is called, we need to set a,b's valPtr to point to x,y's valPtr.
     * This is done using valPtrPtr.
     *
     * *a.valPtrPtr = &x.valPtr;
     */
    DataContainerValue** valPtrPtr = nullptr;

    DataContainerValue* getVal()
    {
        return valPtr;
    };
};

#endif // ABSTRACTDATACONTAINER_H
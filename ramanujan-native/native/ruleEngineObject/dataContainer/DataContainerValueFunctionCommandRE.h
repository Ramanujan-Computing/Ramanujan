//
// Created by Pranav on 28/09/25.
//

#ifndef NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDRE_H
#define NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDRE_H

#include "ArrayRE.h"
#include <utility> // for std::move

class DataContainerValueFunctionCommandRE {
public:
    
public:
    // Ultra-fast default constructor - no allocations at all
    DataContainerValueFunctionCommandRE() = default;

    // can represent both double value or double ptr or arrayValue array.
    //union {
        double value;
        double *arrayValuePtr = nullptr;
    //};

    /**
     * Generic pointer slot used to save/restore class-object references during
     * function calls.  Set and read exclusively by ObjectDataContainerValue's
     * save/restore/propagate methods.  nullptr when the argument is not an object.
     */
    void* objectPtr = nullptr;

    // Ultra-fast destructor - only cleanup when needed
    ~DataContainerValueFunctionCommandRE()  = default;
    
    // High-performance copy constructor with union handling

    
    // High-performance assignment operator with union management

    
    // Ultra-fast move constructor with union transfer

    
    // Ultra-fast move assignment with union transfer


    // Ultra-fast copy methods with virtual dispatch - eliminates switch statement overhead

    // Ultra-fast copy for same-type objects with move semantics

};

// Include inline implementations after both classes are fully defined

#endif //NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDRE_H

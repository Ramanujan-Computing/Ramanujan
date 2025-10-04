//
// Created by Pranav on 01/04/24.
//
#include "OperationRE.h"
#include "DataOperation.h"
#include "operatorFunctioning/OperationFunctioning.h"
#include "dataContainer/EquationBasedDataOperation.h"
#include "operatorFunctioning/AddImpl.h"
#include "operatorFunctioning/AssignImpl.h"
#include "operatorFunctioning/DivideImpl.h"
#include "operatorFunctioning/MultiplyImpl.h"
#include "operatorFunctioning/MinusImpl.h"


void OperationRE::process() {
    // Only Assignment operations have the implementation. Its expected that an assign operation would be here.
    operationFunctioning -> set();

#ifdef DEBUG_BUILD
    debugger->commitDebugPoint();
#endif
}

CachedOperationFunctioning *OperationRE::get() {
    return this->operationFunctioning;
}

bool OperationRE::isAssignType() {
    return operationType == "=";
}

OperationFunctioning *OperationRE::getFunctioning() {
    if(operationType == "+") {
        return new AddImpl();
    }
    if(operationType == "=") {
        return new AssignImpl();
    }
    if(operationType == "/") {
        return new DivideImpl();
    }
    if(operationType == "*") {
        return new MultiplyImpl();
    }
    if(operationType == "-") {
        return new MinusImpl();
    }
    return nullptr;

}

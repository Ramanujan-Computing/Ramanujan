//
// Created by Pranav on 01/04/24.
//

#include "EquationBasedDataOperation.h"

EquationBasedDataOperation::EquationBasedDataOperation(CachedOperationFunctioning *operationFunctioning) {
    this->operationFunctioning = operationFunctioning;
}

void EquationBasedDataOperation::set(double value) {

}

double EquationBasedDataOperation::get() {
    return operationFunctioning->get();
}

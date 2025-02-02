//
// Created by Pranav on 01/04/24.
//

#ifndef NATIVE_EQUATIONBASEDDATAOPERATION_H
#define NATIVE_EQUATIONBASEDDATAOPERATION_H


#include "../DataOperation.h"
#include "operatorFunctioning/CachedOperationFunctioning.h"

class EquationBasedDataOperation : public DataOperation {
private:
    CachedOperationFunctioning *operationFunctioning;

public:
    EquationBasedDataOperation(CachedOperationFunctioning *operationFunctioning);

    void set(double value) override;

    double get() override;
};


#endif //NATIVE_EQUATIONBASEDDATAOPERATION_H

//
// Created by Pranav on 01/04/24.
//

#ifndef NATIVE_DATAOPERATION_H
#define NATIVE_DATAOPERATION_H

#include <string>
#include "DebugPoint.h"

class DataOperation {
public:
    virtual void set(double value) = 0;
    virtual double get() = 0;
};
#endif //NATIVE_DATAOPERATION_H

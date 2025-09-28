//
// Created by pranav on 26/3/24.
//

#ifndef NATIVE_VARIABLERE_CPP
#define NATIVE_VARIABLERE_CPP

#include "VariableRE.h"
#include "DataContainerValueFunctionCommandRE.h"

void DoublePtr::copyDataContainerValue(DataContainerValueFunctionCommandRE& toBeCopied) {
    *value = (toBeCopied.value);
}

void DoublePtr::setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) {
    toBeSet.value = *value;
}

#endif


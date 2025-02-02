//
// Created by Pranav on 10/06/24.
//

#ifndef NATIVE_OPERATIONREPROCESSING_H
#define NATIVE_OPERATIONREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../OperationRE.h"

class OperationReProcessing : public CommandTypeProcessingDefinition {
private:
    OperationRE* operationRe;
public:
    OperationReProcessing(OperationRE* operationRe) {
        this->operationRe = operationRe;
    }

    void get() override {
        operationRe->get()->get();
    }
};
#endif //NATIVE_OPERATIONREPROCESSING_H

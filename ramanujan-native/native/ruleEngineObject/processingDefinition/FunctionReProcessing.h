//
// Created by Pranav on 10/06/24.
//

#ifndef NATIVE_FUNCTIONREPROCESSING_H
#define NATIVE_FUNCTIONREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../FunctionCommandRE.h"

class FunctionReProcessing : public CommandTypeProcessingDefinition {
private:
    FunctionCommandRE* functionRe;

public:
    FunctionReProcessing(FunctionCommandRE* functionRe) {
        this->functionRe = functionRe;
    }

    void get() override {
//        functionRe->processMethod();
    }


};
#endif //NATIVE_FUNCTIONREPROCESSING_H

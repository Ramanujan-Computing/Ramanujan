//
// Created by Pranav on 10/06/24.
//

#ifndef NATIVE_CONSTANTREPROCESSING_H
#define NATIVE_CONSTANTREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../ruleEngineObject/ConstantRE.h"

class ConstantReProcessing : public CommandTypeProcessingDefinition {
private:
    ConstantRE *constantRE;

public:

    ConstantReProcessing(ConstantRE *constantRE) {
        this->constantRE = constantRE;
    }

    void get() override {
    }
};
#endif //NATIVE_CONSTANTREPROCESSING_H

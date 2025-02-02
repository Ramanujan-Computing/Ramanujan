//
// Created by Pranav on 10/06/24.
//

#ifndef NATIVE_VARIABLEREPROCESSING_H
#define NATIVE_VARIABLEREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../ruleEngineObject/dataContainer/VariableRE.h"

class VariableReProcessing : public CommandTypeProcessingDefinition {
private:
    VariableRE *variableRe;

public:

    VariableReProcessing(VariableRE *variableRe) {
        this->variableRe = variableRe;
    }

    void get() override {
    }
};
#endif //NATIVE_VARIABLEREPROCESSING_H

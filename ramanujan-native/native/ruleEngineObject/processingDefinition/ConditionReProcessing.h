//
// Created by Pranav on 10/06/24.
//

#ifndef NATIVE_CONDITIONREPROCESSING_H
#define NATIVE_CONDITIONREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../ConditionRE.h"

class ConditionReProcessing : public
        CommandTypeProcessingDefinition {
private:
    ConditionRE* conditionRe;

public:
    ConditionReProcessing(ConditionRE* conditionRe) {
        this->conditionRe = conditionRe;
    }

    void get() override {
    }
};
#endif //NATIVE_CONDITIONREPROCESSING_H

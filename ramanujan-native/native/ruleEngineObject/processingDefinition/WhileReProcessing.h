//
// Created by Pranav on 10/06/24.
//

#ifndef NATIVE_WHILEREPROCESSING_H
#define NATIVE_WHILEREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../CommandProcessing.h"
#include "../WhileRE.h"

class WhileReProcessing : public CommandTypeProcessingDefinition {
private:
    WhileRE* whileRe;

public:
    WhileReProcessing(WhileRE* whileRe) {
        this->whileRe = whileRe;
    }

    void get() override {
        whileRe->process();
    }

};
#endif //NATIVE_WHILEREPROCESSING_H

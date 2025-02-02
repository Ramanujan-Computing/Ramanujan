//
// Created by Pranav on 10/06/24.
//

#ifndef NATIVE_IFREPROCESSING_H
#define NATIVE_IFREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../IfRE.h"

class IfReProcessing : public CommandTypeProcessingDefinition {
private:
    IfRE* ifRe;

public:
    IfReProcessing(IfRE* ifRe) {
        this->ifRe = ifRe;
    }

    void get() override {
        ifRe->process();
    }

};
#endif //NATIVE_IFREPROCESSING_H

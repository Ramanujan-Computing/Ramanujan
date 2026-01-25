//
// Created by ramanujan on 25/1/26.
//

#ifndef NATIVE_RETURNOPERATIONREPROCESSING_H
#define NATIVE_RETURNOPERATIONREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../ReturnOperationRE.h"

class ReturnOperationReProcessing : public CommandTypeProcessingDefinition {
private:
    ReturnOperationRE* returnOperationRe;
public:
    ReturnOperationReProcessing(ReturnOperationRE* returnOperationRe) {
        this->returnOperationRe = returnOperationRe;
    }

    void get() override {
        // ReturnOperationRE doesn't have a get() method like OperationRE
        // It directly processes the assignment, so we don't need to call anything here
    }
};
#endif //NATIVE_RETURNOPERATIONREPROCESSING_H

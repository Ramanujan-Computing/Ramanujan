//
// Created by Pranav on 31/03/24.
//

#ifndef NATIVE_BOOLEANWRAPPER_H
#define NATIVE_BOOLEANWRAPPER_H

#include "conditionFunctioning/CachedConditionFunctioning.h"

class BooleanWrapper {
private:
    bool val;
    CachedConditionFunctioning* commandProcessing;
public:
    BooleanWrapper(bool val) {
        this->val = val;
    }

    BooleanWrapper(CachedConditionFunctioning *commandProcessing) {
        this->commandProcessing = commandProcessing;
    }

    bool getVal() {
        if(commandProcessing != nullptr) {
            return commandProcessing->operate();
        }
        return val;
    }
};
#endif //NATIVE_BOOLEANWRAPPER_H

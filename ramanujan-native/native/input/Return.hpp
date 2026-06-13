#ifndef RETURN_H
#define RETURN_H

#include <string>
#include <vector>
#include "RuleEngineInputUnit.hpp"
#include "../ruleEngineObject/ReturnRE.h"

/**
 * Represents a return statement in a function.
 *
 * Used for tuple unpacking support where functions can return multiple values
 * that are assigned to target variables passed as additional function arguments.
 */
class Return : public RuleEngineInputUnit {
    public:
        std::vector<std::string> returnValueIds;

        RuleEngineInputUnits* getInternalAnalogy() override {
            return new ReturnRE(returnValueIds);
        }
};

#endif

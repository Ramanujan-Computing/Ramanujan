#ifndef RETURN_H
#define RETURN_H

#include <string>
#include <vector>
#include "RuleEngineInputUnit.hpp"
#include "../ruleEngineObject/ReturnRE.h"
#include <json/json.h>

/**
 * Represents a return statement in a function.
 * 
 * Used for tuple unpacking support where functions can return multiple values
 * that are assigned to target variables passed as additional function arguments.
 * 
 * Example:
 *   Python: a, b = get_coords()
 *   Implementation: Target variables (a, b) are passed as additional arguments
 *                   and the function assigns return values to them.
 */
class Return : public RuleEngineInputUnit {
    public:
        std::vector<std::string> returnValueIds;
        
        Return(Json::Value* value) {
            this->id = (*value)["id"].asString();
            
            Json::Value returnValueIdsJSON = (*value)["returnValueIds"];
            for(int i = 0; i < returnValueIdsJSON.size(); i++) {
                this->returnValueIds.push_back(returnValueIdsJSON[i].asString());
            }
        }
        
        RuleEngineInputUnits* getInternalAnalogy() override {
            return new ReturnRE(returnValueIds);
        }
};

#endif

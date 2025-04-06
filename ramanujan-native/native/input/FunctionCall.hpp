#ifndef FUNC_CALL_H
#define FUNC_CALL_H

#include <string>
#include <list>
#include <vector>
#include "RuleEngineInputUnit.hpp"



class FunctionCall : public RuleEngineInputUnit {
    public:
        std::string firstCommandId;
        std::vector<std::string> arguments;
        int argumentsSize = 0;
        std::vector<std::string> allVariablesInMethod;
        int allVariablesInMethodSize = 0;

        FunctionCall(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->firstCommandId = (*value)["firstCommandId"].asString();
            for (int i = 0; i < (*value)["arguments"].size(); i++) {
                this->arguments.push_back((*value)["arguments"][i].asString());
                argumentsSize++;
            }

            for(int i=0; i<(*value)["allVariablesInMethod"].size(); i++){
                this->allVariablesInMethod.push_back((*value)["allVariablesInMethod"][i].asString());
                allVariablesInMethodSize++;
            }
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif
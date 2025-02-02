#ifndef FUNC_CALL_H
#define FUNC_CALL_H

#include <string>
#include <list>
#include "RuleEngineInputUnit.hpp"



class FunctionCall : public RuleEngineInputUnit {
    public:
        std::string firstCommandId;
        std::vector<std::string> arguments;
        std::vector<std::string> allVariablesInMethod;

        FunctionCall(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->firstCommandId = (*value)["firstCommandId"].asString();
            for (int i = 0; i < (*value)["arguments"].size(); i++) {
                this->arguments.push_back((*value)["arguments"][i].asString());
            }

            for(int i=0; i<(*value)["allVariablesInMethod"].size(); i++){
                this->allVariablesInMethod.push_back((*value)["allVariablesInMethod"][i].asString());
            }
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif
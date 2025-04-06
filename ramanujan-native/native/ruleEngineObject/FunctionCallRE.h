//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_FUNCTIONCALLRE_H
#define NATIVE_FUNCTIONCALLRE_H

#include "RuleEngineInputUnit.hpp"
#include "FunctionCall.hpp"
#include "CommandRE.h"
#include "dataContainer/DataContainerRE.h"

class FunctionCallRE : public RuleEngineInputUnits {
public:    
    FunctionCall* functionCall = nullptr;
    RuleEngineInputUnits** arguments = nullptr;
    RuleEngineInputUnits** allVariablesInMethod = nullptr;
    int argSize = 0;
    CommandRE* commmandRe = nullptr;
    std::string firstCommandId;

    bool setFieldDone = false;

    FunctionCallRE(FunctionCall * functionCall1) {
        this->functionCall  = functionCall1;
    }

    void destroy() {
        if(functionCall != nullptr)
            delete functionCall;
        if(arguments != nullptr)
            delete[] arguments;
    }


    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        if(setFieldDone) {
            return;
        }
        firstCommandId = functionCall->firstCommandId;
        commmandRe = dynamic_cast<CommandRE *>(getFromMap(map, firstCommandId));
        argSize = functionCall->argumentsSize;
        arguments = new RuleEngineInputUnits*[argSize];
        auto itr = functionCall->arguments.begin();
        for (int i = 0; i < functionCall->argumentsSize && itr != functionCall->arguments.end(); i++, itr++) {
            arguments[i] = map->at(*itr);
        }
        allVariablesInMethod = new RuleEngineInputUnits*[functionCall->allVariablesInMethodSize];
        for (int i=0; i< functionCall->allVariablesInMethodSize; i++) {
            allVariablesInMethod[i] = map->at(functionCall->allVariablesInMethod[i]);
        }
        setFieldDone = true;
    }

    void process() override {
    }

};
#endif //NATIVE_FUNCTIONCALLRE_H

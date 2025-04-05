#ifndef COMMAND_H
#define COMMAND_H

#include <string>
#include <list>
#include <vector>
#include "FunctionCall.hpp"
#include "ArrayCommand.hpp"
#include "RuleEngineInputUnit.hpp"
#include "../ruleEngineObject/FunctionCommandRE.h"
#include <json/json.h>



class Command : public RuleEngineInputUnit {
    public:
        std::string nextId;
        std::string ifBlocks;
        std::string loops;
        std::string operation;
        std::string constant;
        std::string variableId;
        std::string conditionId;
        std::string whileId;
        FunctionCall* functionCall = nullptr;
        std::vector<std::string> nextDagTriggerIds;
        ArrayCommand* arrayCommand = nullptr;

        Command(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->nextId = (*value)["nextId"].asString();
            this->ifBlocks = (*value)["ifBlocks"].asString();
            this->loops = (*value)["loops"].asString();
            this->operation = (*value)["operation"].asString();
            this->constant = (*value)["constant"].asString();
            this->variableId = (*value)["variableId"].asString();
            this->conditionId = (*value)["conditionId"].asString();
            this->whileId = (*value)["whileId"].asString();
            this->codeStrPtr = (*value)["codeStrPtr"].asInt();
            Json::Value functionCallJSON = (*value)["functionCall"];
            if(!functionCallJSON.isNull()) {
                this->functionCall = new FunctionCall(&functionCallJSON);
            }
            Json::Value arrayCommandJSON = (*value)["arrayCommand"];
            if(!arrayCommandJSON.isNull()) {
                this->arrayCommand = new ArrayCommand(&arrayCommandJSON);
            }
            for (int i = 0; i < (*value)["nextDagTriggerIds"].size(); i++) {
                this->nextDagTriggerIds.push_back((*value)["nextDagTriggerIds"][i].asString());
            }
        }


    RuleEngineInputUnits *getInternalAnalogy();
};


#endif


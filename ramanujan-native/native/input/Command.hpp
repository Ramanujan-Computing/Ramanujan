#ifndef COMMAND_H
#define COMMAND_H

#include <string>
#include <list>
#include <vector>
#include "FunctionCall.hpp"
#include "ArrayCommand.hpp"
#include "RedefineArrayCommand.hpp"
#include "ReturnAssignmentPair.hpp"
#include "RuleEngineInputUnit.hpp"
#include "../ruleEngineObject/FunctionCommandRE.h"
#include <json/json.h>



struct NewObjectCommandInfo {
    std::string className;
    std::string objectHandleId;
};

struct DeleteObjectCommandInfo {
    std::string objectHandleId;
};

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
        std::string returnOperation;
        FunctionCall* functionCall = nullptr;
        std::vector<std::string> nextDagTriggerIds;
        ArrayCommand* arrayCommand = nullptr;
        RedefineArrayCommand* redefineArrayCommand = nullptr;
        bool returnStatement = false;
        std::vector<ReturnAssignmentPair*> returnAssignmentPairs;
        NewObjectCommandInfo* newObjectCommand = nullptr;
        DeleteObjectCommandInfo* deleteObjectCommand = nullptr;

        Command(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->immediateParentRuleEngineInputUnitId = (*value)["immediateParentRuleEngineInputUnitId"].asString();
            this->nextId = (*value)["nextId"].asString();
            this->ifBlocks = (*value)["ifBlocks"].asString();
            this->loops = (*value)["loops"].asString();
            this->operation = (*value)["operation"].asString();
            this->constant = (*value)["constant"].asString();
            this->variableId = (*value)["variableId"].asString();
            this->conditionId = (*value)["conditionId"].asString();
            this->whileId = (*value)["whileId"].asString();
            this->returnOperation = (*value)["returnOperation"].asString();
            this->codeStrPtr = (*value)["codeStrPtr"].asInt();
            this->returnStatement = (*value)["returnStatement"].asBool();
            
            Json::Value functionCallJSON = (*value)["functionCall"];
            if(!functionCallJSON.isNull()) {
                this->functionCall = new FunctionCall(&functionCallJSON);
            }
            Json::Value arrayCommandJSON = (*value)["arrayCommand"];
            if(!arrayCommandJSON.isNull()) {
                this->arrayCommand = new ArrayCommand(&arrayCommandJSON);
            }
            Json::Value redefineArrayCommandJSON = (*value)["redefineArrayCommand"];
            if(!redefineArrayCommandJSON.isNull()) {
                this->redefineArrayCommand = new RedefineArrayCommand(&redefineArrayCommandJSON);
            }
            
            // Parse returnAssignmentPairs array
            Json::Value returnAssignmentPairsJSON = (*value)["returnAssignmentPairs"];
            if (!returnAssignmentPairsJSON.isNull() && returnAssignmentPairsJSON.isArray()) {
                for (int i = 0; i < returnAssignmentPairsJSON.size(); i++) {
                    ReturnAssignmentPair* pair = new ReturnAssignmentPair(&returnAssignmentPairsJSON[i]);
                    this->returnAssignmentPairs.push_back(pair);
                }
            }

            for (int i = 0; i < (*value)["nextDagTriggerIds"].size(); i++) {
                this->nextDagTriggerIds.push_back((*value)["nextDagTriggerIds"][i].asString());
            }

            Json::Value newObjectCommandJSON = (*value)["newObjectCommand"];
            if (!newObjectCommandJSON.isNull()) {
                this->newObjectCommand = new NewObjectCommandInfo();
                this->newObjectCommand->className = newObjectCommandJSON["className"].asString();
                this->newObjectCommand->objectHandleId = newObjectCommandJSON["objectHandleId"].asString();
            }

            Json::Value deleteObjectCommandJSON = (*value)["deleteObjectCommand"];
            if (!deleteObjectCommandJSON.isNull()) {
                this->deleteObjectCommand = new DeleteObjectCommandInfo();
                this->deleteObjectCommand->objectHandleId = deleteObjectCommandJSON["objectHandleId"].asString();
            }
        }


    RuleEngineInputUnits *getInternalAnalogy();
};


#endif


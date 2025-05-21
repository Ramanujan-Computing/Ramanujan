#ifndef REIU_INPUT_H
#define REIU_INPUT_H

#include <string>
#include <list>
#include <vector>
#include "Variable.hpp"
#include "Command.hpp"
#include "If.hpp"
#include "Operation.hpp"
#include "Condition.hpp"
#include "Constant.hpp"
#include "Array.hpp"
#include "FunctionCall.hpp"
#include "While.hpp"
#include "RedefineArrayCommand.hpp"

#include <json/json.h>



class RuleEngineInput {
    public:
        std::vector<Variable *> *variables = new std::vector<Variable *>();
        std::vector<Command *> *commands = new std::vector<Command *>();
        std::vector<If *> *ifBlocks = new std::vector<If *>();
        std::vector<Operation*> *operations = new std::vector<Operation*>();
        std::vector<Condition*> *conditions = new std::vector<Condition*>();
        std::vector<Constant*> *constants = new std::vector<Constant*>();
        std::vector<Array*> *arrays = new std::vector<Array*>();
        std::vector<FunctionCall*> *functionCalls = new std::vector<FunctionCall*>();
        std::vector<While*> *whileBlocks = new std::vector<While*>();
        std::vector<RedefineArrayCommand*> *redefineArrayCommands = new std::vector<RedefineArrayCommand*>();

        RuleEngineInput(Json::Value* value) {
            Json::Value variables = (*value)["variables"];
            for(int i = 0; i < variables.size(); i++) {
                this->variables->push_back(new Variable(&variables[i]));
            }

            Json::Value commands = (*value)["commands"];
            for(int i = 0; i < commands.size(); i++) {
                this->commands->push_back(new Command(&commands[i]));
            }

            Json::Value ifBlocks = (*value)["ifBlocks"];
            for(int i = 0; i < ifBlocks.size(); i++) {
                this->ifBlocks->push_back(new If(&ifBlocks[i]));
            }

            Json::Value operations = (*value)["operations"];
            for(int i = 0; i < operations.size(); i++) {
                this->operations->push_back(new Operation(&operations[i]));
            }

            Json::Value conditions = (*value)["conditions"];
            for(int i = 0; i < conditions.size(); i++) {
                this->conditions->push_back(new Condition(&conditions[i]));
            }

            Json::Value constants = (*value)["constants"];
            for(int i = 0; i < constants.size(); i++) {
                this->constants->push_back(new Constant(&constants[i]));
            }

            Json::Value arrays = (*value)["arrays"];
            for(int i = 0; i < arrays.size(); i++) {
                this->arrays->push_back(new Array(&arrays[i]));
            }

            Json::Value functionCalls = (*value)["functionCalls"];
            for(int i = 0; i < functionCalls.size(); i++) {
                this->functionCalls->push_back(new FunctionCall(&functionCalls[i]));
            }

            Json::Value whileBlocks = (*value)["whileBlocks"];
            for(int i = 0; i < whileBlocks.size(); i++) {
                this->whileBlocks->push_back(new While(&whileBlocks[i]));
            }

            Json::Value redefineArrayCommands = (*value)["redefineArrayCommands"];
            for(int i = 0; i < redefineArrayCommands.size(); i++) {
                this->redefineArrayCommands->push_back(new RedefineArrayCommand(&redefineArrayCommands[i]));
            }
        }
};

#endif
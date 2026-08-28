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
#include "Return.hpp"
#include "ReturnOperation.hpp"

#include "rule_engine_input.pb.h"



class RuleEngineInput {
    public:
        std::vector<MethodAgnosticVariable*> *methodAgnosticVariables = new std::vector<MethodAgnosticVariable*>();
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
        std::vector<ReturnOperation*> *returnOperations = new std::vector<ReturnOperation*>();

        RuleEngineInput(const ramanujan::RuleEngineInput* proto) {
            for (const auto& v : proto->variables())
                this->variables->push_back(new Variable(&v));
            for (const auto& m : proto->method_data_type_agnostic_args())
                this->methodAgnosticVariables->push_back(new MethodAgnosticVariable(&m));
            for (const auto& c : proto->commands())
                this->commands->push_back(new Command(&c));
            for (const auto& i : proto->if_blocks())
                this->ifBlocks->push_back(new If(&i));
            for (const auto& o : proto->operations())
                this->operations->push_back(new Operation(&o));
            for (const auto& c : proto->conditions())
                this->conditions->push_back(new Condition(&c));
            for (const auto& c : proto->constants())
                this->constants->push_back(new Constant(&c));
            for (const auto& a : proto->arrays())
                this->arrays->push_back(new Array(&a));
            for (const auto& fc : proto->function_calls())
                this->functionCalls->push_back(new FunctionCall(&fc));
            for (const auto& w : proto->while_blocks())
                this->whileBlocks->push_back(new While(&w));
            for (const auto& r : proto->redefine_array_commands())
                this->redefineArrayCommands->push_back(new RedefineArrayCommand(&r));
            for (const auto& r : proto->return_operations())
                this->returnOperations->push_back(new ReturnOperation(&r));
        }
};

#endif

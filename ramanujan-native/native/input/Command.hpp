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
#include "rule_engine_input.pb.h"



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

        Command(const ramanujan::Command* p) {
            this->id = p->id();
            this->immediateParentRuleEngineInputUnitId = p->immediate_parent_rule_engine_input_unit_id();
            this->nextId = p->next_id();
            this->ifBlocks = p->if_blocks();
            this->loops = p->loops();
            this->operation = p->operation();
            this->constant = p->constant();
            this->variableId = p->variable_id();
            this->conditionId = p->condition_id();
            this->whileId = p->while_id();
            this->returnOperation = p->return_operation();
            this->codeStrPtr = p->code_str_ptr();
            this->returnStatement = p->return_statement();

            if (p->has_function_call()) {
                this->functionCall = new FunctionCall(&p->function_call());
            }
            if (p->has_array_command()) {
                this->arrayCommand = new ArrayCommand(&p->array_command());
            }
            if (p->has_redefine_array_command()) {
                this->redefineArrayCommand = new RedefineArrayCommand(&p->redefine_array_command());
            }

            for (const auto& pair : p->return_assignment_pairs()) {
                this->returnAssignmentPairs.push_back(new ReturnAssignmentPair(&pair));
            }

            for (const auto& id : p->next_dag_trigger_ids()) {
                this->nextDagTriggerIds.push_back(id);
            }
        }


    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

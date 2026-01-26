//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_WHILERE_H
#define NATIVE_WHILERE_H

#include "While.hpp"
#include "FunctionCommandRE.h"
#include "../ruleEngineObject/ConditionRE.h"
#include "../ruleEngineObject/CommandRE.h"
#include "DebugPoint.h"

class WhileRE : public RuleEngineInputUnits {
private:
    While* whileCommand;
    ConditionRE* conditionRe;
    RuleEngineInputUnits* whileCommandRE;

public:
    WhileRE(While* whileCommand) {
        this->whileCommand = whileCommand;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        id = whileCommand->id;
        immediateParent = getFromMap(map, whileCommand->immediateParentRuleEngineInputUnitId);
        whileCommandRE = (dynamic_cast<CommandRE *>(getFromMap(map, whileCommand->whileCommandId)));
        if (whileCommandRE != nullptr) {
            whileCommandRE = ((CommandRE*)whileCommandRE)->getUnit();
        }
        conditionRe = dynamic_cast<ConditionRE *>(getFromMap(map, whileCommand->conditionId));
        conditionRe->whileUser.insert(this);
    }

    RuleEngineInputUnits* process() override {
#ifdef DEBUG_BUILD
        int debugLine = debugger->getDebugPointToBeCommitted()->line;
        while(true) {
            bool result = conditionFunctioning->operate();
            debugger->startDebugPoint();
            std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
            debugPoint->line = debugLine;
            debugPoint->setCondResult(result);
            debugger->commitDebugPoint();
            if(!result) {
                break;
            }
#else
        while(conditionFunctioning->operate()) {
#endif
            RuleEngineInputUnits* unit = whileCommandRE;
            while(unit != nullptr) {
                unit = unit->process();
            }

             // Check if a return was encountered in the while loop body
            if (encounteredReturn) [[unlikely]] {
                // Propagate return flag to parent
                if (immediateParent != nullptr) {
                    immediateParent->encounteredReturn = true;
                }
                // Disable our flag and return nullptr
                encounteredReturn = false;
                return nullptr;
            }
        }
        return nextUnit;
    }

    void destroy() {
        if(conditionFunctioning != nullptr)
        delete conditionFunctioning;
    }

    CachedConditionFunctioning *conditionFunctioning;
};
#endif //NATIVE_WHILERE_H

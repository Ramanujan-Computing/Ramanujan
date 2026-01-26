//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_IFRE_H
#define NATIVE_IFRE_H

#include "RuleEngineInputUnits.hpp"
#include "FunctionCommandRE.h"
#include "If.hpp"
#include "../ruleEngineObject/ConditionRE.h"
#include "../ruleEngineObject/CommandRE.h"
#include "DebugPoint.h"

class IfRE : public RuleEngineInputUnits {
private:
    If* ifCommand;
    ConditionRE* conditionRe;
    RuleEngineInputUnits* ifCommandRE = nullptr;
    RuleEngineInputUnits* elseCommandRE = nullptr;


public:
    IfRE(If* ifCommand) {
        this->ifCommand = ifCommand;
    }

    void destroy() {
        if(conditionFunctioning != nullptr)
        delete conditionFunctioning;
    }

    CachedConditionFunctioning *conditionFunctioning;

    void
    setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        id = ifCommand->id;
        immediateParent = getFromMap(map, ifCommand->immediateParentRuleEngineInputUnitId);
        conditionRe = dynamic_cast<ConditionRE*> (getFromMap(map, ifCommand->conditionId));
        ifCommandRE = (dynamic_cast<CommandRE*> (getFromMap(map, ifCommand->ifCommand)));
        if (ifCommandRE != nullptr) {
            ifCommandRE = ((CommandRE*)ifCommandRE)->getUnit();
        }
        elseCommandRE = (dynamic_cast<CommandRE*> (getFromMap(map, ifCommand->elseCommand)));
        if (elseCommandRE != nullptr) {
            elseCommandRE = ((CommandRE*)elseCommandRE)->getUnit();
        }
        conditionRe->ifUser.insert(this);
    }

    RuleEngineInputUnits* process() override {

#ifdef DEBUG_BUILD
        std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
#endif
        RuleEngineInputUnits* unit;
        if(conditionFunctioning->operate()) {
            unit = ifCommandRE;
        } else {
            unit = elseCommandRE;
        }
#ifdef DEBUG_BUILD
        debugPoint->setCondResult(result);
        debugger->commitDebugPoint();
#endif

        while(unit != nullptr) {
            unit = unit->process();
        }
        
        // Check if a return was encountered in the if/else block
        if (encounteredReturn) [[unlikely]] {
            // Propagate return flag to parent
            if (immediateParent != nullptr) {
                immediateParent->encounteredReturn = true;
            }
            // Disable our flag and return nullptr
            encounteredReturn = false;
            return nullptr;
        }
        
        return nextUnit;
    }
};

#endif //NATIVE_IFRE_H

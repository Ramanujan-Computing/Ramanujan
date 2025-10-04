//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_IFRE_H
#define NATIVE_IFRE_H

#include "RuleEngineInputUnits.hpp"
#include "If.hpp"
#include "../ruleEngineObject/ConditionRE.h"
#include "../ruleEngineObject/CommandRE.h"
#include "DebugPoint.h"

class IfRE : public RuleEngineInputUnits {
private:
    If* ifCommand;
    ConditionRE* conditionRe;
    CommandRE* ifCommandRE = nullptr;
    CommandRE* elseCommandRE = nullptr;


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
        conditionRe = dynamic_cast<ConditionRE*> (getFromMap(map, ifCommand->conditionId));
        ifCommandRE = dynamic_cast<CommandRE*> (getFromMap(map, ifCommand->ifCommand));
        elseCommandRE = dynamic_cast<CommandRE*> (getFromMap(map, ifCommand->elseCommand));
        conditionRe->ifUser.insert(this);
    }

    void process() override {
#ifdef DEBUG_BUILD
        std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
#endif
        CommandRE* commandRE;
        if(conditionFunctioning->operate()) {
            commandRE = ifCommandRE;
        } else {
            commandRE = elseCommandRE;
        }
#ifdef DEBUG_BUILD
        debugPoint->setCondResult(result);
        debugger->commitDebugPoint();
#endif

        while(commandRE != nullptr) {
            commandRE = commandRE->get();
        }
    }
};

#endif //NATIVE_IFRE_H

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
    CommandRE* whileCommandRE;

public:
    WhileRE(While* whileCommand) {
        this->whileCommand = whileCommand;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        whileCommandRE = dynamic_cast<CommandRE *>(getFromMap(map, whileCommand->whileCommandId));
        conditionRe = dynamic_cast<ConditionRE *>(getFromMap(map, whileCommand->conditionId));
        conditionRe->whileUser.insert(this);
    }

    CommandRE* process() override {
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
            CommandRE* commandRE = whileCommandRE;
            while(commandRE != nullptr) {
                commandRE = commandRE->get();
            }
        }
        // Normal completion of while loop
        if (FunctionCommandRE::hasEncounteredReturn)
        {
            return nullptr;
        }
        return nextCommandRE;
    }

    void destroy() {
        if(conditionFunctioning != nullptr)
        delete conditionFunctioning;
    }

    CachedConditionFunctioning *conditionFunctioning;
};
#endif //NATIVE_WHILERE_H

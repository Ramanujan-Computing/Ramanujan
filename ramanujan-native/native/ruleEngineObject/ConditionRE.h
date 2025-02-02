//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_CONDITIONRE_H
#define NATIVE_CONDITIONRE_H

#include "RuleEngineInputUnits.hpp"
#include "CommandRE.h"
#include "Condition.hpp"
#include "conditionFunctioning/ConditionFunctioning.h"
#include "conditionFunctioning/CachedConditionFunctioning.h"
#include <set>

class ConditionRE : public RuleEngineInputUnits {
private:
    Condition* condition;
    std::string conditionType;
    CommandRE *operandCommandRE1, *operandCommandRE2;
    CachedConditionFunctioning *conditionFunctioning = nullptr;
    bool isCached = false;

public:
    ConditionRE(Condition* condition) {
        this->condition = condition;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        operandCommandRE1 = dynamic_cast<CommandRE *>(getFromMap(map, condition->comparisionCommand1));
        operandCommandRE2 = dynamic_cast<CommandRE *>(getFromMap(map, condition->comparisionCommand2));
        conditionType = condition->conditionType;
    }

    void process() override;
    bool operate();

    ConditionFunctioning *getFunctioning();

    void setCachedConditionFunctioning();

    std::set<WhileRE*> whileUser;
    std::set<IfRE*> ifUser;

    void destroy() {
        if(conditionFunctioning != nullptr)
            delete conditionFunctioning;

        // destroy whileUser and ifUser
        for(auto whileRE : whileUser) {
            whileUser.erase(whileRE);
        }
        for(auto ifRE : ifUser) {
            ifUser.erase(ifRE);
        }
    }
};
#endif //NATIVE_CONDITIONRE_H

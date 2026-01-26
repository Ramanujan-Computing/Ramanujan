//
// Created by Pranav on 12/06/24.
//

#ifndef NATIVE_DEFAULTRULEENGINEUNITS_H
#define NATIVE_DEFAULTRULEENGINEUNITS_H

#include "RuleEngineInputUnits.hpp"

class DefaultRuleEngineUnits : public RuleEngineInputUnits {
public:
    RuleEngineInputUnits* process() override {
        return nextUnit;
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {

    }

    void destroy() override {

    }

};
#endif //NATIVE_DEFAULTRULEENGINEUNITS_H

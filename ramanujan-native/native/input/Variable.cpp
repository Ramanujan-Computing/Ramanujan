//
// Created by pranav on 26/3/24.
//

#ifndef VARIABLE_CPP
#define VARIABLE_CPP

#include "Variable.hpp"
#include "../ruleEngineObject/dataContainer/VariableRE.h"

RuleEngineInputUnits* Variable::getInternalAnalogy() {
    return new VariableRE(this);
}

Variable::Variable(const ramanujan::Variable* p) {
    id = p->id();
    name = p->name();
    dataType = p->data_type();
    value = p->value();
    frameCount = p->frame_count();
}

RuleEngineInputUnits* MethodAgnosticVariable::getInternalAnalogy() {
    return new MethodAgnosticVariableRE(this);
}

MethodAgnosticVariable::MethodAgnosticVariable(const ramanujan::MethodDataTypeAgnosticArg* p) {
    id = p->id();
    name = p->name();
    value = p->value();
    frameCount = p->frame_count();
}




#endif

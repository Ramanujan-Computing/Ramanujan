#ifndef VARIABLE_H
#define VARIABLE_H

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class Variable : public RuleEngineInputUnit {
    public:
    Variable(const ramanujan::Variable* p);

    std::string name, dataType, frameCount;
        double value;

    RuleEngineInputUnits *getInternalAnalogy();
};

class MethodAgnosticVariable : public RuleEngineInputUnit {
public:
    MethodAgnosticVariable(const ramanujan::MethodDataTypeAgnosticArg* p);

    std::string name, dataType, frameCount;
    double value;

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif

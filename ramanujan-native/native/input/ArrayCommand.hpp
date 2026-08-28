#ifndef ARRAY_COMMAND_H
#define ARRAY_COMMAND_H

#include <string>
#include <list>
#include <vector>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class ArrayCommand : public RuleEngineInputUnit {
    public:
        std::string arrayId;
        std::vector<std::string*> *index;

        ArrayCommand(const ramanujan::ArrayCommand* p) {
            this->arrayId = p->array_id();
            this->index = new std::vector<std::string*>();
            for (const auto& s : p->index()) {
                this->index->push_back(new std::string(s));
            }
        }

    RuleEngineInputUnits *getInternalAnalogy() {
            return nullptr;
        }
};


#endif

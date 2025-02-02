#ifndef ARRAY_COMMAND_H
#define ARRAY_COMMAND_H

#include <string>
#include <list>
#include "RuleEngineInputUnit.hpp"



class ArrayCommand : public RuleEngineInputUnit {
    public:
        std::string arrayId;
        std::vector<std::string*> *index;

        ArrayCommand(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->arrayId = (*value)["arrayId"].asString();
            this->index = new std::vector<std::string*>();
            for (int i = 0; i < (*value)["index"].size(); i++) {
                this->index->push_back(new std::string((*value)["index"][i].asString()));
            }
        }

    RuleEngineInputUnits *getInternalAnalogy() {
            return nullptr;
        }
};


#endif


//
// Created by pranav on 2/3/24.
//

#ifndef NATIVE_RULEENGINEINPUTUNITS_HPP
#define NATIVE_RULEENGINEINPUTUNITS_HPP


#include <string>
#include <unordered_map>
#include "CommandProcessing.h"

class CommandRE;  // Forward declaration

class RuleEngineInputUnits {
protected:

    int codeStrPtr;

public:
    std::string id;
    CommandRE* nextCommandRE = nullptr;  // Next command to execute after this unit
    virtual void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) = 0;
    virtual CommandRE* process() = 0;  // Returns next command (nullptr on return statement)
    std::string getId() {
        return id;
    }

public: RuleEngineInputUnits* getFromMap(std::unordered_map<std::string, RuleEngineInputUnits *> *map, std::string key) {
        std::unordered_map<std::string, RuleEngineInputUnits* >::iterator itr = map->find(key);
        if(itr == map->end()) {
            return nullptr;
        }
        return itr->second;
    }

    ~RuleEngineInputUnits() {
        destroy();
    }

private:
    virtual void destroy() = 0;
};


#endif //NATIVE_RULEENGINEINPUTUNITS_HPP

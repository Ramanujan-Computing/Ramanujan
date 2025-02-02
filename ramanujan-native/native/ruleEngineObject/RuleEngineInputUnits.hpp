//
// Created by pranav on 2/3/24.
//

#ifndef NATIVE_RULEENGINEINPUTUNITS_HPP
#define NATIVE_RULEENGINEINPUTUNITS_HPP


#include <string>
#include <unordered_map>
#include "CommandProcessing.h"



class RuleEngineInputUnits {
protected:

    int codeStrPtr;

public:
    std::string id;
    virtual void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) = 0;
    virtual void process() = 0;
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

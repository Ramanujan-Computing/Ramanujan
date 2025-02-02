#ifndef REIU_H
#define REIU_H

#include <string>
#include <list>
#include "../ruleEngineObject/RuleEngineInputUnits.hpp"
#include <json/json.h>



class RuleEngineInputUnit {
    public:
        std::string id;
        int codeStrPtr;

        /**
         * This class is an input from network, there is an internal object which needs to be created for this impl,
         * the new object would be used in the computation.
         */
        virtual RuleEngineInputUnits * getInternalAnalogy() = 0;

        std::string getId() {
            return id;
        }
};

#endif
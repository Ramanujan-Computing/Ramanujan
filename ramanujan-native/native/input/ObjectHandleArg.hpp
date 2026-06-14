#ifndef NATIVE_OBJECTHANDLEARG_HPP
#define NATIVE_OBJECTHANDLEARG_HPP

#include <string>
#include "RuleEngineInputUnit.hpp"
#include "../ruleEngineObject/dataContainer/ObjectHandleArgRE.h"
#include <json/json.h>

/**
 * IR representation of an object-typed function parameter.
 * Produced by the Java compiler when a parameter has a class-name type annotation.
 * At runtime, getInternalAnalogy() returns an ObjectHandleArgRE whose
 * currentObjectHandleId is filled in by FunctionCommandRE during each call.
 */
class ObjectHandleArg : public RuleEngineInputUnit {
public:
    std::string className;
    int frameCount = 0;

    ObjectHandleArg(Json::Value* value) {
        this->id        = (*value)["id"].asString();
        this->className = (*value)["className"].asString();
        this->frameCount = (*value)["frameCount"].asInt();
    }

    RuleEngineInputUnits* getInternalAnalogy() override {
        ObjectHandleArgRE* re = new ObjectHandleArgRE(className);
        re->id = this->id;
        return re;
    }
};

#endif // NATIVE_OBJECTHANDLEARG_HPP

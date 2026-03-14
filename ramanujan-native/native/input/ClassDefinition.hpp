#ifndef CLASS_DEFINITION_H
#define CLASS_DEFINITION_H

#include <string>
#include <vector>
#include <json/value.h>
#include "RuleEngineInputUnit.hpp"

/**
 * Represents the definition (blueprint) of a class parsed from RuleEngineInput JSON.
 *
 * ClassDefinition holds the schema for a class: its name, field names, method
 * function IDs, and the constructor function ID.  When an object is instantiated
 * (see ObjectInstance), per-instance field Variables are created and the
 * constructor FunctionCall is invoked with those field Variable IDs passed by
 * reference.
 *
 * This structure is metadata produced during compilation; at execution time, class
 * fields are represented as regular Variable instances and method calls as
 * FunctionCall entries — no special execution logic is required.
 *
 * Example JSON:
 * {
 *   "id": "classDef_Person",
 *   "className": "Person",
 *   "fieldNames": ["name", "age"],
 *   "constructorFunctionId": "Person___init__",
 *   "methodFunctionIds": ["Person_greet"]
 * }
 */
class ClassDefinition : public RuleEngineInputUnit {
public:
    /** Unqualified class name, e.g. "Person". */
    std::string className;

    /**
     * Names of instance fields declared via self.field = ... in __init__.
     * Order matches the per-field parameters added to every method that takes self.
     */
    std::vector<std::string> fieldNames;

    /**
     * Qualified function IDs for each non-constructor method, e.g. "Person_greet".
     * These correspond to FunctionCall id entries in the RuleEngineInput.
     */
    std::vector<std::string> methodFunctionIds;

    /**
     * Function ID of the constructor (__init__), e.g. "Person___init__".
     * Empty string when the class has no explicit constructor.
     */
    std::string constructorFunctionId;

    ClassDefinition(Json::Value* value) {
        this->id = (*value)["id"].asString();
        this->className = (*value)["className"].asString();
        this->constructorFunctionId = (*value)["constructorFunctionId"].asString();
        Json::Value fieldNamesJson = (*value)["fieldNames"];
        for (int i = 0; i < (int)fieldNamesJson.size(); i++) {
            this->fieldNames.push_back(fieldNamesJson[i].asString());
        }
        Json::Value methodIdsJson = (*value)["methodFunctionIds"];
        for (int i = 0; i < (int)methodIdsJson.size(); i++) {
            this->methodFunctionIds.push_back(methodIdsJson[i].asString());
        }
    }

    RuleEngineInputUnits* getInternalAnalogy();
};

#endif // CLASS_DEFINITION_H

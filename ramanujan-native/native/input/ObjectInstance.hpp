#ifndef OBJECT_INSTANCE_H
#define OBJECT_INSTANCE_H

#include <string>
#include <unordered_map>
#include <json/value.h>

/**
 * Represents a concrete object instance created from a class definition.
 *
 * Each time obj = ClassName(args) is compiled, an ObjectInstance is created
 * that binds the instance's logical field names to the unique Variable IDs
 * generated for that specific instance.  The same Variable IDs are passed
 * (by reference) whenever a method is invoked on the object, ensuring that
 * mutations inside the method are reflected in the caller's scope.
 *
 * Copy-by-reference semantics: passing an object to a function or method call
 * passes the field Variable IDs stored in fieldVariableIds.  Since the rule
 * engine works with Variable IDs (references), any mutation inside the callee
 * is automatically visible to the caller.
 *
 * Example JSON:
 * {
 *   "id": "objInst_p",
 *   "instanceName": "p",
 *   "className": "Person",
 *   "fieldVariableIds": {
 *     "name": "<uuid-for-name>",
 *     "age":  "<uuid-for-age>"
 *   }
 * }
 */
class ObjectInstance {
public:
    /** Unique identifier for this object instance. */
    std::string id;

    /** Variable name used in source code, e.g. "p". */
    std::string instanceName;

    /** Name of the class this instance belongs to, e.g. "Person". */
    std::string className;

    /**
     * Maps each field name to the unique Variable ID that stores its value for
     * this particular instance.  These IDs reference Variable entries in the
     * enclosing RuleEngineInput.
     */
    std::unordered_map<std::string, std::string> fieldVariableIds;

    ObjectInstance(Json::Value* value) {
        this->id = (*value)["id"].asString();
        this->instanceName = (*value)["instanceName"].asString();
        this->className = (*value)["className"].asString();
        Json::Value fieldVarIds = (*value)["fieldVariableIds"];
        for (Json::Value::iterator it = fieldVarIds.begin(); it != fieldVarIds.end(); ++it) {
            this->fieldVariableIds[it.key().asString()] = it->asString();
        }
    }
};

#endif // OBJECT_INSTANCE_H

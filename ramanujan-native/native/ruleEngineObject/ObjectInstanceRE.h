//
// ObjectInstanceRE.h
//

#ifndef NATIVE_OBJECTINSTANCERE_H
#define NATIVE_OBJECTINSTANCERE_H

#include "RuleEngineInputUnits.hpp"
#include "dataContainer/VariableRE.h"
#include "../input/ObjectInstance.hpp"
#include <unordered_map>
#include <string>

/**
 * Rule engine representation of a concrete object instance (ObjectInstanceRE).
 *
 * ObjectInstanceRE is the runtime mirror of ObjectInstance.  It pre-resolves
 * each field name to the live VariableRE* that backs that field for this
 * specific instance, enabling O(1) field lookup by name and providing a
 * single authoritative location for field introspection at debug / trace time.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * COPY-BY-REFERENCE SEMANTICS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * When the compiler emits a method call on an object, it passes the object's
 * field Variable IDs directly as method arguments.  Inside FunctionCommandRE
 * these IDs are resolved with:
 *
 *     callingArg = map->at(functionCommandInfo->arguments[i])  // → VariableRE*
 *
 * Because FunctionCommandRE stores only DataContainerValue* pointers (not
 * copies of the values), the method and the caller share the *same* VariableRE
 * and therefore the same underlying double storage.  Any write to a field
 * inside the callee is immediately visible in the calling scope once the method
 * returns — this is the copy-by-reference contract described in ObjectInstance.
 *
 * ObjectInstanceRE does NOT participate in value computation; process() is a
 * no-op.  It exists to:
 *   • provide a clean API for looking up the VariableRE of any field by name,
 *   • allow the debugger / trace layer to enumerate all live fields of an
 *     instance without iterating the whole global map, and
 *   • make the boundary between "object metadata" and "plain variables" explicit
 *     in the runtime object graph.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class ObjectInstanceRE : public RuleEngineInputUnits {
    ObjectInstance* objectInstance;

    /**
     * Maps each field name to the live VariableRE that holds its value for this
     * instance.  Populated during setFields() after all VariableRE objects have
     * been registered in the global map.
     */
    std::unordered_map<std::string, VariableRE*> fieldREMap;

public:
    /**
     * Constructs an ObjectInstanceRE from the corresponding input struct.
     * Field pointer resolution is deferred to setFields().
     */
    explicit ObjectInstanceRE(ObjectInstance* instance) {
        this->objectInstance = instance;
        this->id = instance->id;
    }

    /**
     * Resolves each field's Variable ID to its live VariableRE* from the map.
     * Called during Processor::fixGraph() after all Variable RE objects have
     * been registered.
     *
     * @param map  Global ID → RuleEngineInputUnits* map
     */
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*>* map) override {
        for (const auto& entry : objectInstance->fieldVariableIds) {
            const std::string& fieldName = entry.first;
            const std::string& varId     = entry.second;
            auto it = map->find(varId);
            if (it != map->end()) {
                VariableRE* re = dynamic_cast<VariableRE*>(it->second);
                if (re != nullptr) {
                    fieldREMap[fieldName] = re;
                }
            }
        }
    }

    /**
     * Object instances do not execute — this is a metadata-only node.
     * @return nullptr always
     */
    RuleEngineInputUnits* process() override {
        return nullptr;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    /** @return The source-code variable name for this instance, e.g. "p". */
    const std::string& getInstanceName() const {
        return objectInstance->instanceName;
    }

    /** @return The class this instance belongs to, e.g. "Person". */
    const std::string& getClassName() const {
        return objectInstance->className;
    }

    /**
     * Looks up the live VariableRE for a given field by name.
     *
     * @param fieldName  e.g. "name" or "age"
     * @return  The resolved VariableRE*, or nullptr if the field is unknown.
     */
    VariableRE* getFieldRE(const std::string& fieldName) const {
        auto it = fieldREMap.find(fieldName);
        return (it != fieldREMap.end()) ? it->second : nullptr;
    }

    /**
     * @return  The full field-name → VariableRE* map for this instance.
     *          Useful for iterating all fields (e.g. in a debugger).
     */
    const std::unordered_map<std::string, VariableRE*>& getFieldREMap() const {
        return fieldREMap;
    }

    void destroy() override {}
};

#endif // NATIVE_OBJECTINSTANCERE_H
